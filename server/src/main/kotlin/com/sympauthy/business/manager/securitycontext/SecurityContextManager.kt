package com.sympauthy.business.manager.securitycontext

import com.sympauthy.business.mapper.SecurityContextMapper
import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.business.model.securitycontext.ObservedSecurityContext
import com.sympauthy.business.model.securitycontext.SecurityContext
import com.sympauthy.business.model.securitycontext.SecurityContextField
import com.sympauthy.business.model.securitycontext.SecurityContextGeo
import com.sympauthy.business.model.securitycontext.fingerprint
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.SecurityContextEntity
import com.sympauthy.data.repository.SecurityContextRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

private const val USER_AGENT = "User-Agent"

/**
 * Reads what a request shows of where it came from, and keeps one record per place rather than one
 * per request.
 *
 * Which headers may be believed is a deployment's to say, and saying it wrong is the sharpest edge in
 * this feature: a header is read only because an operator named the proxy that writes it, and naming
 * one is a promise that the origin cannot be reached around that proxy. A deployment that has not
 * made that promise leaves the configuration empty and gets the address of the socket, which no
 * caller can choose.
 *
 * Nothing here is transactional. Recording a context joins the transaction of whatever is writing the
 * row that will point at it — the interactive flow session it was observed for — so a session and the
 * place it was created from are written together or not at all.
 */
@Singleton
class SecurityContextManager(
    @Inject private val advancedConfig: AdvancedConfig,
    @Inject private val securityContextRepository: SecurityContextRepository,
    @Inject private val securityContextMapper: SecurityContextMapper
) {

    /**
     * What [request] shows of where it came from: the caller's address, the user agent it sent, and
     * whatever geo the proxy in front of this deployment published about it.
     *
     * Every field is null where nothing supplied it. Reading one is never a failure — an operator's
     * mistake here is a record with less in it, never a request that does not complete.
     */
    suspend fun getObservedSecurityContext(request: ObservedRequest): ObservedSecurityContext {
        val config = advancedConfig.orThrow().securityContext
        return ObservedSecurityContext(
            ip = config.read(SecurityContextField.CLIENT_IP, request),
            userAgent = request.headerOrNull(USER_AGENT),
            geo = SecurityContextGeo(
                country = config.read(SecurityContextField.COUNTRY, request),
                region = config.read(SecurityContextField.REGION, request),
                city = config.read(SecurityContextField.CITY, request)
            )
        )
    }

    /**
     * Record that [request] was seen, and answer the context it came from, reading it the way this
     * deployment says its proxy publishes what it saw.
     */
    suspend fun recordObservation(
        request: ObservedRequest,
        knownContextIds: List<UUID> = emptyList(),
        userId: UUID? = null
    ): SecurityContext = recordObservation(getObservedSecurityContext(request), knownContextIds, userId)

    /**
     * Record that [observed] was seen, and answer the context it belongs to.
     *
     * A place already among [knownContextIds] — the contexts the session making the request carries —
     * is the same place seen again: its last sighting and its count move on, and no row is written.
     * Failing that, a place [userId] has already been seen in is theirs, which is what keeps a
     * signed-in person's history to one row per place across sessions. Only a place neither of them
     * knows is a new row.
     *
     * The context is attached to [userId] as it is created, so a sighting after a sign-in is kept for
     * as long as a user's places are, and one before it for as long as an unattached one is.
     */
    suspend fun recordObservation(
        observed: ObservedSecurityContext,
        knownContextIds: List<UUID> = emptyList(),
        userId: UUID? = null
    ): SecurityContext {
        val fingerprint = observed.fingerprint
        val known = knownContextIds
            .takeIf(List<UUID>::isNotEmpty)
            ?.let { securityContextRepository.findByIdIn(it) }
            ?.firstOrNull { it.fingerprint == fingerprint }
        val seen = known ?: userId?.let { securityContextRepository.findByUserIdAndFingerprint(it, fingerprint) }
        val now = LocalDateTime.now()

        if (seen == null) {
            return saveFirstSighting(observed, fingerprint, userId, now)
        }

        val observationCount = seen.observationCount + 1
        val expirationDate = now.plus(retentionOf(seen.userId))
        securityContextRepository.updateLastSeenDate(
            id = seen.id!!,
            lastSeenDate = now,
            observationCount = observationCount,
            expirationDate = expirationDate
        )
        return securityContextMapper.toSecurityContext(seen).copy(
            lastSeenDate = now,
            observationCount = observationCount,
            expirationDate = expirationDate
        )
    }

    /**
     * Attach every context of [contextIds] nobody was attached to yet to [userId].
     *
     * Capture happens before anyone has signed in, so without this the day an unattached context is
     * kept for would cut a person's history off at the moment it started mattering.
     *
     * A promoted context may be a place [userId] has already been seen in — the same person, the same
     * place, an earlier visit. The row that was already theirs survives, keeping the earlier first
     * sighting, the later last one and the sum of the two counts, and the promoted row is deleted.
     *
     * The answer is those deletions: every id that no longer exists, by the id that stands for it now,
     * and empty where nothing collided. The caller's own record of the contexts it observed has to
     * follow them.
     */
    suspend fun promoteToUser(contextIds: List<UUID>, userId: UUID): Map<UUID, UUID> {
        if (contextIds.isEmpty()) return emptyMap()
        val retention = retentionOf(userId)
        val merged = mutableMapOf<UUID, UUID>()

        securityContextRepository.findByIdIn(contextIds)
            .filter { it.userId == null }
            .forEach { promoted ->
                val theirs = securityContextRepository.findByUserIdAndFingerprint(userId, promoted.fingerprint)
                if (theirs == null) {
                    securityContextRepository.updateUserId(
                        id = promoted.id!!,
                        userId = userId,
                        expirationDate = promoted.lastSeenDate.plus(retention)
                    )
                } else {
                    val lastSeenDate = maxOf(theirs.lastSeenDate, promoted.lastSeenDate)
                    securityContextRepository.updateFirstSeenDate(
                        id = theirs.id!!,
                        firstSeenDate = minOf(theirs.firstSeenDate, promoted.firstSeenDate),
                        lastSeenDate = lastSeenDate,
                        observationCount = theirs.observationCount + promoted.observationCount,
                        expirationDate = lastSeenDate.plus(retention)
                    )
                    securityContextRepository.deleteByIdIn(listOf(promoted.id!!))
                    merged[promoted.id!!] = theirs.id!!
                }
            }

        return merged
    }

    private suspend fun saveFirstSighting(
        observed: ObservedSecurityContext,
        fingerprint: String,
        userId: UUID?,
        now: LocalDateTime
    ): SecurityContext {
        val entity = securityContextRepository.save(
            SecurityContextEntity(
                userId = userId,
                fingerprint = fingerprint,
                ip = observed.ip,
                userAgent = observed.userAgent,
                country = observed.geo.country,
                region = observed.geo.region,
                city = observed.geo.city,
                firstSeenDate = now,
                lastSeenDate = now,
                observationCount = 1,
                expirationDate = now.plus(retentionOf(userId))
            )
        )
        return securityContextMapper.toSecurityContext(entity)
    }

    /**
     * How long a context attached to [userId] is kept, counted from its last sighting. A context
     * nobody is attached to is the short-lived one: it comes from an abandoned flow, a failed sign-in
     * or probing.
     */
    private fun retentionOf(userId: UUID?): Duration = advancedConfig.orThrow().securityContext.let {
        if (userId == null) it.unknownRetention else it.knownRetention
    }

    /**
     * The value [field] takes on [request]: the header this deployment bound the field to, read as it
     * stands, or the configured extraction's own rule where it bound none.
     *
     * An override replaces the rule rather than standing in front of it, so a named header that did
     * not arrive answers null. Falling back would mean a field whose value cannot be traced to either
     * the header the operator named or the one the profile knows.
     */
    private fun SecurityContextConfig.read(
        field: SecurityContextField,
        request: ObservedRequest
    ): String? {
        val overriddenBy = headers[field]
        return if (overriddenBy != null) {
            request.headerOrNull(overriddenBy)
        } else {
            profile.read(field, request)
        }
    }
}

package com.sympauthy.business.manager.consent

import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.ConsentMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.user.User
import com.sympauthy.data.model.ConsentEntity
import com.sympauthy.data.repository.ConsentRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

/**
 * Manages user consents granted during authorization flows.
 *
 * A consent records which scopes a user has authorized for a given audience. Only one active (non-revoked) consent
 * may exist per user+audience pair at any time. When a new consent is granted for an existing pair, the previous
 * consent is automatically revoked and replaced.
 *
 * Revoking a consent also revokes every grant of the user for the audience, so no client goes on acting for the
 * user under it. An act-as grant rests on no consent, and is left to the act-as rules.
 *
 * Consents only apply to user-facing flows (not client_credentials).
 */
@Singleton
open class ConsentManager(
    @Inject private val consentRepository: ConsentRepository,
    @Inject private val grantManager: GrantManager,
    @Inject private val consentMapper: ConsentMapper,
    @Inject private val userManager: UserManager,
    @Inject private val lockManager: LockManager
) {

    /**
     * Save a granted consent for the given [user] and [audience] with the given [scopes].
     * The [clientId] is recorded for audit purposes (which client prompted this consent update). It stays an
     * identifier rather than a [com.sympauthy.business.model.client.Client]: this method writes it into the
     * consent and never reads the client it names.
     *
     * Since consent is audience-level, different clients in the same audience may collect different scopes
     * across separate authorization flows. The new [scopes] are merged with any existing active consent
     * for this user+audience pair, so that previously consented scopes are preserved.
     *
     * If an active consent already exists, it is revoked and replaced by a new consent containing
     * the union of existing and new scopes.
     */
    @Transactional
    open suspend fun saveConsent(
        user: User,
        audience: Audience,
        clientId: String,
        scopes: List<String>
    ): Consent {
        // By id though [user] is in hand: promotion is the record's state, not its identity, so it is read
        // again inside this transaction rather than taken from a copy the caller may have read earlier.
        userManager.checkPromoted(user.id)
        val existingConsent = consentRepository.findByUserIdAndAudienceIdAndRevokedAtIsNull(user.id, audience.id)
        val mergedScopes = if (existingConsent != null) {
            consentRepository.updateRevokedAt(
                id = existingConsent.id!!,
                revokedAt = LocalDateTime.now(),
                revokedBy = ConsentRevokedBy.USER.name,
                revokedById = user.id
            )
            (existingConsent.scopes.toList() + scopes).distinct()
        } else {
            scopes
        }

        val entity = ConsentEntity(
            userId = user.id,
            audienceId = audience.id,
            promptedByClientId = clientId,
            scopes = mergedScopes.toTypedArray(),
            consentedAt = LocalDateTime.now()
        )
        val savedEntity = consentRepository.save(entity)
        return consentMapper.toConsent(savedEntity)
    }

    /**
     * Find the active (non-revoked) consent for the given [userId] and [audienceId], or null if none exists.
     */
    suspend fun findActiveConsentByAudienceOrNull(userId: UUID, audienceId: String): Consent? {
        return consentRepository.findByUserIdAndAudienceIdAndRevokedAtIsNull(userId, audienceId)
            ?.let(consentMapper::toConsent)
    }

    /**
     * Find all active (non-revoked) consents for the given [userId].
     */
    suspend fun findActiveConsentsByUser(userId: UUID): List<Consent> {
        return consentRepository.findByUserIdAndRevokedAtIsNull(userId)
            .map(consentMapper::toConsent)
    }

    /**
     * Find all active (non-revoked) consents for the given [audienceId].
     */
    suspend fun findActiveConsentsByAudience(audienceId: String): List<Consent> {
        return consentRepository.findByAudienceIdAndRevokedAtIsNull(audienceId)
            .map(consentMapper::toConsent)
    }

    /**
     * List one page of the active (non-revoked) consents granted for the given [audience], oldest first.
     *
     * When a [providerId] is given, only the consents of users linked to that provider are returned, and a
     * [subject] narrows that further to the single account bearing it. A [subject] without a [providerId] is
     * meaningless and is ignored; the caller is expected to have refused the combination already.
     *
     * The order is by consent date and total, so two calls agree on a snapshot. It is not append-only, which
     * is the departure the API standard's ordering rule allows for and names: [saveConsent] revokes and
     * re-inserts rather than updating, so a user consenting again moves to the tail, and a caller walking every
     * page while that happens may miss one or see one twice.
     */
    suspend fun listActiveConsentsByAudience(
        audience: Audience,
        providerId: String?,
        subject: String?,
        page: Int,
        size: Int
    ): List<Consent> {
        val offset = page * size
        val entities = if (providerId != null) {
            consentRepository.findActiveByAudienceIdAndProvider(
                audienceId = audience.id,
                providerId = providerId,
                subject = subject,
                limit = size,
                offset = offset
            )
        } else {
            consentRepository.findActiveByAudienceId(
                audienceId = audience.id,
                limit = size,
                offset = offset
            )
        }
        return entities.map(consentMapper::toConsent)
    }

    /**
     * Count the active (non-revoked) consents granted for the given [audience], under the same [providerId]
     * and [subject] filter [listActiveConsentsByAudience] applies.
     */
    suspend fun countActiveConsentsByAudience(
        audience: Audience,
        providerId: String?,
        subject: String?
    ): Int {
        val count = if (providerId != null) {
            consentRepository.countActiveByAudienceIdAndProvider(
                audienceId = audience.id,
                providerId = providerId,
                subject = subject
            )
        } else {
            consentRepository.countActiveByAudienceId(audience.id)
        }
        return count.toInt()
    }

    /**
     * Revoke the given [consent], and every grant of its user for its audience except the act-as ones.
     *
     * Each of the audience's clients acts under the one consent, so a grant opened for the audience rests on it
     * whichever client holds it — not only one opened for the client that last updated it, which
     * [Consent.promptedByClientId] records and nothing more. An act-as grant rests on the act-as rules instead,
     * and is left in service.
     *
     * Both are written holding the lock over the account a refresh of those grants takes, so a refresh
     * racing this either reads the consent revoked or has the grant it issued under revoked with the rest.
     */
    @Transactional
    open suspend fun revokeConsent(
        consent: Consent,
        revokedBy: ConsentRevokedBy,
        revokedById: UUID
    ) {
        lockManager.withLock(listOf(LockKey.User(consent.userId))) {
            val now = LocalDateTime.now()
            val updatedCount = consentRepository.updateRevokedAt(
                id = consent.id,
                revokedAt = now,
                revokedBy = revokedBy.name,
                revokedById = revokedById
            )
            if (updatedCount > 0) {
                grantManager.revokeGrantsByUserAndAudience(
                    userId = consent.userId,
                    audienceId = consent.audienceId,
                    revokedAt = now,
                    revokedBy = GrantRevokedBy.CONSENT_REVOKED,
                    revokedById = revokedById
                )
            }
        }
    }
}

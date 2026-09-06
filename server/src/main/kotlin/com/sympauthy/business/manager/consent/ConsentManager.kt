package com.sympauthy.business.manager.consent

import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.ConsentMapper
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.business.model.oauth2.TokenRevokedBy
import com.sympauthy.data.model.ConsentEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
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
 * Revoking a consent also cascades to all refresh tokens issued for that user+client pair, effectively
 * preventing the client from obtaining new access tokens on behalf of the user.
 *
 * Consents only apply to user-facing flows (not client_credentials).
 */
@Singleton
open class ConsentManager(
    @Inject private val consentRepository: ConsentRepository,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val consentMapper: ConsentMapper,
    @Inject private val userManager: UserManager
) {

    /**
     * Save a granted consent for the given [userId] and [audienceId] with the given [scopes].
     * The [clientId] is recorded for audit purposes (which client prompted this consent update).
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
        userId: UUID,
        audienceId: String,
        clientId: String,
        scopes: List<String>
    ): Consent {
        userManager.checkPromoted(userId)
        val existingConsent = consentRepository.findByUserIdAndAudienceIdAndRevokedAtIsNull(userId, audienceId)
        val mergedScopes = if (existingConsent != null) {
            consentRepository.updateRevokedAt(
                id = existingConsent.id!!,
                revokedAt = LocalDateTime.now(),
                revokedBy = ConsentRevokedBy.USER.name,
                revokedById = userId
            )
            (existingConsent.scopes.toList() + scopes).distinct()
        } else {
            scopes
        }

        val entity = ConsentEntity(
            userId = userId,
            audienceId = audienceId,
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
     * List one page of the active (non-revoked) consents granted for the given [audienceId], oldest first.
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
        audienceId: String,
        providerId: String?,
        subject: String?,
        page: Int,
        size: Int
    ): List<Consent> {
        val offset = page * size
        val entities = if (providerId != null) {
            consentRepository.findActiveByAudienceIdAndProvider(
                audienceId = audienceId,
                providerId = providerId,
                subject = subject,
                limit = size,
                offset = offset
            )
        } else {
            consentRepository.findActiveByAudienceId(
                audienceId = audienceId,
                limit = size,
                offset = offset
            )
        }
        return entities.map(consentMapper::toConsent)
    }

    /**
     * Count the active (non-revoked) consents granted for the given [audienceId], under the same [providerId]
     * and [subject] filter [listActiveConsentsByAudience] applies.
     */
    suspend fun countActiveConsentsByAudience(
        audienceId: String,
        providerId: String?,
        subject: String?
    ): Int {
        val count = if (providerId != null) {
            consentRepository.countActiveByAudienceIdAndProvider(
                audienceId = audienceId,
                providerId = providerId,
                subject = subject
            )
        } else {
            consentRepository.countActiveByAudienceId(audienceId)
        }
        return count.toInt()
    }

    /**
     * Revoke the given [consent] and all associated refresh tokens for this user+client pair.
     */
    @Transactional
    open suspend fun revokeConsent(
        consent: Consent,
        revokedBy: ConsentRevokedBy,
        revokedById: UUID
    ) {
        val updatedCount = consentRepository.updateRevokedAt(
            id = consent.id,
            revokedAt = LocalDateTime.now(),
            revokedBy = revokedBy.name,
            revokedById = revokedById
        )
        if (updatedCount > 0) {
            tokenRepository.updateRevokedAtByUserIdAndClientId(
                userId = consent.userId,
                clientId = consent.promptedByClientId,
                revokedAt = LocalDateTime.now(),
                revokedBy = TokenRevokedBy.CONSENT_REVOKED.name,
                revokedById = revokedById
            )
        }
    }
}

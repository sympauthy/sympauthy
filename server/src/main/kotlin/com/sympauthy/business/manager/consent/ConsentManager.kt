package com.sympauthy.business.manager.consent

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
 * Manages user consents granted to clients during authorization flows.
 *
 * A consent records which scopes a user has authorized for a given client. Only one active (non-revoked) consent
 * may exist per user+client pair at any time. When a new consent is granted for an existing pair, the previous
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
    @Inject private val consentMapper: ConsentMapper
) {

    /**
     * Save a granted consent for the given [userId] and [clientId] with the given [scopes].
     * If an active consent already exists for this user+client pair, it is revoked and replaced.
     */
    @Transactional
    open suspend fun saveGrantedConsent(
        userId: UUID,
        clientId: String,
        scopes: List<String>
    ): Consent {
        val existingConsent = consentRepository.findByUserIdAndClientIdAndRevokedAtIsNull(userId, clientId)
        if (existingConsent != null) {
            consentRepository.updateRevokedAt(
                id = existingConsent.id!!,
                revokedAt = LocalDateTime.now(),
                revokedBy = ConsentRevokedBy.USER.name,
                revokedById = userId
            )
        }

        val entity = ConsentEntity(
            userId = userId,
            clientId = clientId,
            scopes = scopes.toTypedArray(),
            consentedAt = LocalDateTime.now()
        )
        val savedEntity = consentRepository.save(entity)
        return consentMapper.toConsent(savedEntity)
    }

    /**
     * Find the active (non-revoked) consent for the given [userId] and [clientId], or null if none exists.
     */
    suspend fun findActiveConsentOrNull(userId: UUID, clientId: String): Consent? {
        return consentRepository.findByUserIdAndClientIdAndRevokedAtIsNull(userId, clientId)
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
     * Find all active (non-revoked) consents for the given [clientId].
     */
    suspend fun findActiveConsentsByClient(clientId: String): List<Consent> {
        return consentRepository.findByClientIdAndRevokedAtIsNull(clientId)
            .map(consentMapper::toConsent)
    }

    /**
     * Find all consents (including revoked) for the given [userId].
     */
    suspend fun findAllConsentsByUser(userId: UUID): List<Consent> {
        return consentRepository.findByUserId(userId)
            .map(consentMapper::toConsent)
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
                clientId = consent.clientId,
                revokedAt = LocalDateTime.now(),
                revokedBy = TokenRevokedBy.CONSENT_REVOKED.name,
                revokedById = revokedById
            )
        }
    }
}

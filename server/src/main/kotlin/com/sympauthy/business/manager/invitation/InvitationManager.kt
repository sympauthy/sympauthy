package com.sympauthy.business.manager.invitation

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.mapper.InvitationMapper
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.InvitationAdvancedConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.InvitationEntity
import com.sympauthy.data.repository.InvitationRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

/**
 * Manager for creating, looking up, consuming, and revoking invitations.
 *
 * Invitation tokens are stored as scrypt hashes with a SHA-256 lookup hash for O(1) retrieval.
 * Raw tokens are returned only once at creation time and never stored in plaintext.
 */
@Singleton
open class InvitationManager(
    @Inject private val invitationRepository: InvitationRepository,
    @Inject private val invitationHashGenerator: InvitationHashGenerator,
    @Inject private val invitationTokenGenerator: InvitationTokenGenerator,
    @Inject private val invitationMapper: InvitationMapper,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig
) {

    private val invitationConfig: InvitationAdvancedConfig
        get() = uncheckedAdvancedConfig.orThrow().invitationConfig

    /**
     * Create a new invitation. Returns the [Invitation] model and the raw base64url-encoded token.
     * The raw token is only available at creation time.
     */
    @Transactional
    open suspend fun createInvitation(
        audienceId: String,
        claims: Map<String, String>?,
        note: String?,
        expiresAt: LocalDateTime?,
        createdBy: InvitationCreatedBy,
        createdById: String? = null
    ): Pair<Invitation, String> {
        val config = invitationConfig
        val now = LocalDateTime.now()

        val resolvedExpiresAt = resolveExpiresAt(now, expiresAt, config)

        val tokenBytes = invitationTokenGenerator.generate(config.tokenLengthInBytes)
        val encodedToken = invitationTokenGenerator.encode(tokenBytes)
        val tokenPrefix = invitationTokenGenerator.extractPrefix(encodedToken)
        val lookupHash = invitationHashGenerator.computeLookupHash(tokenBytes)
        val salt = invitationTokenGenerator.generate(config.hashConfig.saltLengthInBytes)
        val hashedToken = invitationHashGenerator.hash(tokenBytes, salt)

        val entity = InvitationEntity(
            audienceId = audienceId,
            tokenLookupHash = lookupHash,
            hashedToken = hashedToken,
            salt = salt,
            tokenPrefix = tokenPrefix,
            claims = claims,
            note = note,
            status = InvitationStatus.PENDING.name,
            createdBy = createdBy.name,
            createdById = createdById,
            createdAt = now,
            expiresAt = resolvedExpiresAt,
        )
        val saved = invitationRepository.save(entity)
        return invitationMapper.toInvitation(saved) to encodedToken
    }

    private fun resolveExpiresAt(
        now: LocalDateTime,
        requestedExpiresAt: LocalDateTime?,
        config: InvitationAdvancedConfig
    ): LocalDateTime {
        val maxExpiresAt = now.plus(config.maxExpiration)
        if (requestedExpiresAt == null) {
            return now.plus(config.defaultExpiration)
        }
        return if (requestedExpiresAt.isAfter(maxExpiresAt)) maxExpiresAt else requestedExpiresAt
    }

    suspend fun findByIdOrNull(id: UUID): Invitation? {
        return invitationRepository.findById(id)
            ?.let(invitationMapper::toInvitation)
    }

    suspend fun findById(id: UUID): Invitation {
        return findByIdOrNull(id) ?: throw businessExceptionOf(
            detailsId = "invitation.not_found",
            values = arrayOf("id" to id.toString())
        )
    }

    suspend fun findByAudienceId(audienceId: String): List<Invitation> {
        return invitationRepository.findByAudienceId(audienceId)
            .map(invitationMapper::toInvitation)
    }

    suspend fun findByCreatedById(createdById: String): List<Invitation> {
        return invitationRepository.findByCreatedById(createdById)
            .map(invitationMapper::toInvitation)
    }

    /**
     * Look up an invitation by raw token. Returns null if no matching invitation is found
     * or if the scrypt verification fails.
     */
    suspend fun findByRawTokenOrNull(rawToken: String): Invitation? {
        val tokenBytes = try {
            invitationTokenGenerator.decode(rawToken)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val lookupHash = invitationHashGenerator.computeLookupHash(tokenBytes)
        val entity = invitationRepository.findByTokenLookupHash(lookupHash) ?: return null

        if (!invitationHashGenerator.verify(tokenBytes, entity.salt, entity.hashedToken)) {
            return null
        }
        return invitationMapper.toInvitation(entity)
    }

    /**
     * Validate a raw invitation token for use during the authorize flow.
     * Checks that the invitation exists, is pending, not expired, and matches the client's audience.
     */
    suspend fun validateToken(rawToken: String, clientAudienceId: String): Invitation {
        val invitation = findByRawTokenOrNull(rawToken)
            ?: throw recoverableBusinessExceptionOf(
                detailsId = "invitation.invalid_token",
                descriptionId = "description.invitation.invalid_token"
            )

        when (invitation.status) {
            InvitationStatus.CONSUMED -> throw recoverableBusinessExceptionOf(
                detailsId = "invitation.already_consumed",
                descriptionId = "description.invitation.already_consumed"
            )
            InvitationStatus.REVOKED -> throw recoverableBusinessExceptionOf(
                detailsId = "invitation.revoked",
                descriptionId = "description.invitation.revoked"
            )
            InvitationStatus.EXPIRED -> throw recoverableBusinessExceptionOf(
                detailsId = "invitation.expired",
                descriptionId = "description.invitation.expired"
            )
            InvitationStatus.PENDING -> { /* valid */ }
        }

        if (invitation.audienceId != clientAudienceId) {
            throw businessExceptionOf(
                detailsId = "invitation.audience_mismatch",
                values = arrayOf(
                    "invitationAudience" to invitation.audienceId,
                    "clientAudience" to clientAudienceId
                )
            )
        }

        return invitation
    }

    /**
     * Mark an invitation as consumed by the given user.
     */
    @Transactional
    open suspend fun consumeInvitation(id: UUID, userId: UUID): Invitation {
        val now = LocalDateTime.now()
        invitationRepository.updateStatus(
            id = id,
            status = InvitationStatus.CONSUMED.name,
            consumedByUserId = userId,
            consumedAt = now
        )
        return findById(id)
    }

    /**
     * Revoke a pending invitation.
     */
    @Transactional
    open suspend fun revokeInvitation(id: UUID): Invitation {
        val invitation = findById(id)
        if (invitation.status != InvitationStatus.PENDING) {
            throw businessExceptionOf(
                detailsId = "invitation.cannot_revoke",
                values = arrayOf("status" to invitation.status.name)
            )
        }
        invitationRepository.updateRevokedAt(
            id = id,
            status = InvitationStatus.REVOKED.name,
            revokedAt = LocalDateTime.now()
        )
        return findById(id)
    }
}

package com.sympauthy.business.manager.reauth

import com.sympauthy.business.mapper.ReAuthenticationAttemptMapper
import com.sympauthy.business.model.reauth.PassedReAuthenticationAttempt
import com.sympauthy.business.model.reauth.PendingReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationMethod
import com.sympauthy.business.model.reauth.ReAuthenticationPurpose
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.ReAuthenticationAttemptEntity
import com.sympauthy.data.repository.ReAuthenticationAttemptRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

/**
 * Manager in charge of the lifecycle of a generic [ReAuthenticationAttempt] (forced re-login / step-up).
 *
 * This manager is intentionally decoupled from any consumer feature: it only knows how to create a challenge for a
 * target user, record that the ownership proof was provided, and expose the current state. Verification of the
 * credential itself is performed by the existing sign-in managers (password / provider), which then call
 * [markPassed] and hand off to the consumer's completion handler.
 */
@Singleton
class ReAuthenticationManager(
    @Inject private val reAuthenticationAttemptRepository: ReAuthenticationAttemptRepository,
    @Inject private val reAuthenticationAttemptMapper: ReAuthenticationAttemptMapper,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Create and persist a new [PendingReAuthenticationAttempt] requiring the end-user to prove ownership of
     * [targetUserId] before the [purpose] action is performed.
     */
    suspend fun start(
        targetUserId: UUID,
        purpose: ReAuthenticationPurpose
    ): PendingReAuthenticationAttempt {
        val now = LocalDateTime.now()
        val entity = ReAuthenticationAttemptEntity(
            targetUserId = targetUserId,
            purpose = purpose.name,
            attemptDate = now,
            expirationDate = now.plus(uncheckedAuthConfig.orThrow().authorizationCode.expiration)
        )
        val saved = reAuthenticationAttemptRepository.save(entity)
        return reAuthenticationAttemptMapper.toPendingReAuthenticationAttempt(saved)
    }

    /**
     * Return the [ReAuthenticationAttempt] identified by [id], or null if it does not exist.
     */
    suspend fun findByIdOrNull(id: UUID): ReAuthenticationAttempt? {
        return reAuthenticationAttemptRepository.findById(id)
            ?.let(reAuthenticationAttemptMapper::toReAuthenticationAttempt)
    }

    /**
     * Return the [PendingReAuthenticationAttempt] identified by [id] only if it is still pending, not expired, and
     * matches the expected [purpose]. Returns null otherwise.
     */
    suspend fun getPendingOrNull(
        id: UUID,
        purpose: ReAuthenticationPurpose
    ): PendingReAuthenticationAttempt? {
        return (findByIdOrNull(id) as? PendingReAuthenticationAttempt)
            ?.takeIf { !it.expired && it.purpose == purpose }
    }

    /**
     * Record that the end-user proved ownership of the target account using [method], and return the updated
     * [PassedReAuthenticationAttempt].
     */
    suspend fun markPassed(
        attempt: PendingReAuthenticationAttempt,
        method: ReAuthenticationMethod
    ): PassedReAuthenticationAttempt {
        val passedDate = LocalDateTime.now()
        reAuthenticationAttemptRepository.updatePassedDatePassedMethod(
            id = attempt.id,
            passedDate = passedDate,
            passedMethod = method.name
        )
        return PassedReAuthenticationAttempt(
            id = attempt.id,
            targetUserId = attempt.targetUserId,
            purpose = attempt.purpose,
            expirationDate = attempt.expirationDate,
            attemptDate = attempt.attemptDate,
            passedDate = passedDate,
            passedMethod = method
        )
    }
}

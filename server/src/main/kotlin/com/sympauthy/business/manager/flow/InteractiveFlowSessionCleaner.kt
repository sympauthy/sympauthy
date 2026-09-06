package com.sympauthy.business.manager.flow

import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.InteractiveFlowSessionConfirmRepository
import com.sympauthy.data.repository.InteractiveFlowSessionLinkProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import com.sympauthy.data.repository.InteractiveFlowSessionProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionReauthenticationRepository
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.TotpEnrollmentRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Component in charge of cleaning expired interactive flow sessions, their attached records and direct
 * dependencies — and the accounts an abandoned sign-up left half-created.
 *
 * The two are one job because they are one ordering. A session references the account it was signing up, and
 * that account's own rows reference it back, so the session goes first, then the account's rows, then the
 * account. Collecting the account is what makes a sign-up all-or-nothing from the other end: what the flow
 * wrote is either promoted when it completes (see
 * [com.sympauthy.business.manager.user.ProvisionalAccountManager]) or removed here when it never does, and
 * the personal details of an abandoned sign-up are not kept indefinitely.
 */
@Singleton
open class InteractiveFlowSessionCleaner(
    @Inject private val sessionRepository: InteractiveFlowSessionRepository,
    @Inject private val oauth2Repository: InteractiveFlowSessionOAuth2Repository,
    @Inject private val providerRepository: InteractiveFlowSessionProviderRepository,
    @Inject private val confirmRepository: InteractiveFlowSessionConfirmRepository,
    @Inject private val reauthenticationRepository: InteractiveFlowSessionReauthenticationRepository,
    @Inject private val linkProviderRepository: InteractiveFlowSessionLinkProviderRepository,
    @Inject private val validationCodeRepository: ValidationCodeRepository,
    @Inject private val authorizationCodeRepository: AuthorizationCodeRepository,
    @Inject private val userRepository: UserRepository,
    @Inject private val passwordRepository: PasswordRepository,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val providerUserInfoRepository: ProviderUserInfoRepository,
    @Inject private val totpEnrollmentRepository: TotpEnrollmentRepository,
) {

    @Transactional
    open suspend fun clean(): CleanResult = coroutineScope {
        val expiredSessions = sessionRepository.findExpired()
        val expiredSessionIds = expiredSessions.mapNotNull(InteractiveFlowSessionEntity::id)

        // Delete the direct dependencies (all FK to interactive_flow_sessions) before the sessions themselves.
        val deferredAuthorizationCodesCount = async {
            authorizationCodeRepository.deleteBySessionIdIn(expiredSessionIds)
        }
        val deferredValidationCodesCount = async {
            validationCodeRepository.deleteBySessionIdIn(expiredSessionIds)
        }
        val deferredOAuth2Count = async {
            oauth2Repository.deleteBySessionIdIn(expiredSessionIds)
        }
        val deferredProviderCount = async {
            providerRepository.deleteBySessionIdIn(expiredSessionIds)
        }
        val deferredConfirmCount = async {
            confirmRepository.deleteBySessionIdIn(expiredSessionIds)
        }
        val deferredReauthenticationCount = async {
            reauthenticationRepository.deleteBySessionIdIn(expiredSessionIds)
        }
        val deferredLinkProviderCount = async {
            linkProviderRepository.deleteBySessionIdIn(expiredSessionIds)
        }

        val authorizationCodesCount = deferredAuthorizationCodesCount.await()
        val validationCodesCount = deferredValidationCodesCount.await()
        deferredOAuth2Count.await()
        deferredProviderCount.await()
        deferredConfirmCount.await()
        deferredReauthenticationCount.await()
        deferredLinkProviderCount.await()

        val sessionsCount = sessionRepository.deleteByIds(expiredSessionIds)

        CleanResult(
            sessionCount = sessionsCount,
            authorizationCodeCount = authorizationCodesCount,
            validationCodesCount = validationCodesCount,
            abandonedAccountCount = collectAbandonedAccounts()
        )
    }

    /**
     * Delete the accounts left behind by a sign-up that never completed, and answer how many there were.
     *
     * Runs after the sessions are gone, because that absence is what marks an account abandoned — see
     * [UserRepository.findAbandoned], which is also where the rule for skipping an account something still
     * refers to lives. The account's own rows go first, every one of them rather than only the provisional
     * ones: the account is going, so anything hanging off it is going too.
     */
    private suspend fun collectAbandonedAccounts(): Int {
        val userIds = userRepository.findAbandoned().mapNotNull(UserEntity::id)
        if (userIds.isEmpty()) return 0

        passwordRepository.deleteByUserIdIn(userIds)
        collectedClaimRepository.deleteByUserIdIn(userIds)
        providerUserInfoRepository.deleteByUserIdIn(userIds)
        totpEnrollmentRepository.deleteByUserIdIn(userIds)

        return userRepository.deleteByIdIn(userIds)
    }

    data class CleanResult(
        val sessionCount: Int,
        val authorizationCodeCount: Int,
        val validationCodesCount: Int,
        val abandonedAccountCount: Int,
    )
}

package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.user.ProvisionalAccountManager
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.InteractiveFlowSessionConfirmRepository
import com.sympauthy.data.repository.InteractiveFlowSessionLinkProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import com.sympauthy.data.repository.InteractiveFlowSessionProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionReauthenticationRepository
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
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
 * The two are one job because they are one ordering. A session references the account it was signing up, so
 * the session goes first and the account after it — which is also what marks the account abandoned. Removing
 * it belongs to [ProvisionalAccountManager], which owns both ends of a provisional account's life; this
 * cleaner owns when that happens.
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
    @Inject private val provisionalAccountManager: ProvisionalAccountManager,
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
            // Last, because a session still present is what keeps an account from counting as abandoned.
            abandonedAccountCount = provisionalAccountManager.deleteAbandoned()
        )
    }

    data class CleanResult(
        val sessionCount: Int,
        val authorizationCodeCount: Int,
        val validationCodesCount: Int,
        val abandonedAccountCount: Int,
    )
}

package com.sympauthy.business.manager.flow

import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
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
 * dependencies.
 *
 * Deleting a session is what marks an account it was signing up abandoned, but collecting that account is
 * not a step of this. [com.sympauthy.business.manager.user.ProvisionalAccountManager.deleteAbandoned] keys
 * on the session being gone rather than on the sessions any one run expired, so it is a cleaner of its own
 * on a cron of its own — which is what has it read an absence this transaction has committed rather than one
 * only this transaction can see. Keeping the two apart is also what stops this transaction holding a
 * session's lock while it waits for an account's: a flow's completion takes the two in the opposite order.
 *
 * One run takes at most `advanced.cleanup.batch-size` sessions. Every delete below names them in an `IN`
 * list, so an unbounded backlog would be a write holding locks for as long as it takes — and, past the
 * bind parameters a statement admits, one the database refuses. See [InteractiveFlowSessionRepository].
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
    @Inject private val advancedConfig: AdvancedConfig,
) {

    @Transactional
    open suspend fun clean(): CleanResult = coroutineScope {
        val batchSize = advancedConfig.orThrow().cleanup.batchSize
        val expiredSessions = sessionRepository.findExpired(batchSize)
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
            moreToClean = expiredSessions.size == batchSize,
        )
    }

    data class CleanResult(
        val sessionCount: Int,
        val authorizationCodeCount: Int,
        val validationCodesCount: Int,
        /**
         * Whether the run took as many sessions as it was allowed, so there may be more the next run will
         * take.
         */
        val moreToClean: Boolean,
    )
}

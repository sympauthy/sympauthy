package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.lock.LeasedJob.CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.repository.InteractiveFlowSessionConfirmRepository
import com.sympauthy.data.repository.InteractiveFlowSessionLinkProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import com.sympauthy.data.repository.InteractiveFlowSessionPasswordResetRepository
import com.sympauthy.data.repository.InteractiveFlowSessionProviderRepository
import com.sympauthy.data.repository.InteractiveFlowSessionReauthenticationRepository
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import com.sympauthy.data.repository.InteractiveFlowSessionSecurityContextRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import com.sympauthy.monitoring.telemetry.manager.FlowMetrics
import com.sympauthy.monitoring.telemetry.manager.JobMetrics
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Component in charge of cleaning expired interactive flow sessions, their attached records and direct
 * dependencies.
 *
 * The authorization code a session completed with is not one of them: it outlives the session, and
 * [com.sympauthy.business.manager.grant.GrantCleaner] collects it.
 *
 * Neither is the account an expired session was signing up: deleting the session is what marks it
 * abandoned, and [com.sympauthy.business.manager.user.ProvisionalAccountManager.deleteAbandoned] collects it
 * on a cron of its own, for the reasons `docs/design/provisional-user.md` gives.
 */
@Singleton
open class InteractiveFlowSessionCleaner(
    @Inject private val sessionRepository: InteractiveFlowSessionRepository,
    @Inject private val oauth2Repository: InteractiveFlowSessionOAuth2Repository,
    @Inject private val providerRepository: InteractiveFlowSessionProviderRepository,
    @Inject private val confirmRepository: InteractiveFlowSessionConfirmRepository,
    @Inject private val reauthenticationRepository: InteractiveFlowSessionReauthenticationRepository,
    @Inject private val passwordResetRepository: InteractiveFlowSessionPasswordResetRepository,
    @Inject private val linkProviderRepository: InteractiveFlowSessionLinkProviderRepository,
    @Inject private val securityContextRepository: InteractiveFlowSessionSecurityContextRepository,
    @Inject private val validationCodeRepository: ValidationCodeRepository,
    @Inject private val flowMetrics: FlowMetrics,
    @Inject private val jobMetrics: JobMetrics
) {

    /**
     * Delete the expired sessions and what they hold, and record the sessions and validation codes deleted under
     * [CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS].
     *
     * The attached records and the sessions they belong to are deleted together, so a run failing partway — a
     * session delete refused by a row written against it after its records went — leaves every session it read
     * standing whole for the next run, rather than still in place with its records already gone.
     */
    @Transactional
    open suspend fun clean(): CleanResult = coroutineScope {
        val expiredSessions = sessionRepository.findExpired()
        val expiredSessionIds = expiredSessions.mapNotNull(InteractiveFlowSessionEntity::id)

        // Delete the direct dependencies (all FK to interactive_flow_sessions) before the sessions themselves.
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
        val deferredPasswordResetCount = async {
            passwordResetRepository.deleteBySessionIdIn(expiredSessionIds)
        }
        val deferredLinkProviderCount = async {
            linkProviderRepository.deleteBySessionIdIn(expiredSessionIds)
        }
        // Only the places of flows that never completed are still here, several per session where a session
        // was driven from several: a completed flow folded the one a credential was proven at into the
        // person's record and consumed every row in the same transaction.
        val deferredSecurityContextCount = async {
            securityContextRepository.deleteBySessionIdIn(expiredSessionIds)
        }

        val validationCodesCount = deferredValidationCodesCount.await()
        deferredOAuth2Count.await()
        deferredProviderCount.await()
        deferredConfirmCount.await()
        deferredReauthenticationCount.await()
        deferredPasswordResetCount.await()
        deferredLinkProviderCount.await()
        deferredSecurityContextCount.await()

        val sessionsCount = sessionRepository.deleteByIds(expiredSessionIds)
        expiredSessions.forEach(::recordIfUnfinished)
        val job = CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS.leaseName
        jobMetrics.recordDeletions(job, "interactive_flow_session", sessionsCount.toLong())
        jobMetrics.recordDeletions(job, "validation_code", validationCodesCount.toLong())

        CleanResult(
            sessionCount = sessionsCount,
            validationCodesCount = validationCodesCount,
        )
    }

    /**
     * Record the expired [session] on `sympauthy.flow.sessions.expired` if it expired unfinished, under the first
     * of its purposes it had not finished. A session that completed, failed or was cancelled stays until it
     * expires too, and was recorded when it ended.
     */
    private fun recordIfUnfinished(session: InteractiveFlowSessionEntity) {
        if (session.completeDate != null || session.cancelDate != null || session.errorDate != null) return
        val stalledPurpose = session.purposes.firstOrNull { it !in session.completedPurposes }
        flowMetrics.recordExpired(
            purpose = session.initiatingPurpose.lowercase(),
            stalledPurpose = stalledPurpose?.lowercase(),
            clientId = session.initiatingClientId
        )
    }

    data class CleanResult(
        val sessionCount: Int,
        val validationCodesCount: Int,
    )
}

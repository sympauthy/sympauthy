package com.sympauthy.cron

import com.sympauthy.business.manager.auth.GrantCleaner
import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.business.manager.lock.LeasedJob.CLEAN_FINISHED_GRANTS
import com.sympauthy.monitoring.loggerForClass
import com.sympauthy.monitoring.telemetry.JobMetrics
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Deletes the expired tokens and authorization codes, and the grants that are finished.
 *
 * A job of its own rather than a step of [CleanExpiredInteractiveFlowSessionCron]: a grant outlives the
 * session it came from by as long as its client goes on refreshing, and so does the authorization code naming
 * it, for as long as its own lifetime runs.
 *
 * The lease keeps a deployment of several instances from sweeping several times over, and the sweep stays
 * correct when it runs twice anyway — it deletes what is committed-expired or committed-revoked, which is the
 * same set on every instance. [JobLeaseManager] carries why a leased job has to be one of those.
 */
@Singleton
class CleanFinishedGrantCron(
    @Inject private val grantCleaner: GrantCleaner,
    @Inject private val jobLeaseManager: JobLeaseManager,
    @Inject private val jobMetrics: JobMetrics
) {
    private val logger = loggerForClass()

    @OptIn(DelicateCoroutinesApi::class)
    @Scheduled(fixedDelay = "15m")
    fun clean() {
        GlobalScope.launch {
            jobLeaseManager.withLease(CLEAN_FINISHED_GRANTS) {
                val result = grantCleaner.clean()
                val job = CLEAN_FINISHED_GRANTS.leaseName
                jobMetrics.recordDeletions(job, "token", result.tokenCount.toLong())
                jobMetrics.recordDeletions(job, "authorization_code", result.authorizationCodeCount.toLong())
                jobMetrics.recordDeletions(job, "grant", result.grantCount.toLong())
                if (result.tokenCount > 0 || result.authorizationCodeCount > 0 || result.grantCount > 0) {
                    logger.debug {
                        "Cleaned ${result.tokenCount} tokens, ${result.authorizationCodeCount} authorization codes " +
                            "and ${result.grantCount} finished grants."
                    }
                }
            }
        }
    }
}

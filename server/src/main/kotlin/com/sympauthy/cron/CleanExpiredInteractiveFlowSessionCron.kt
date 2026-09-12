package com.sympauthy.cron

import com.sympauthy.business.manager.flow.InteractiveFlowSessionCleaner
import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.business.manager.lock.LeasedJob.CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Expires the interactive flow sessions whose time is up, and the records attached to them.
 *
 * The lease is what keeps a deployment of several instances from doing that work several times over. It is
 * not what keeps it correct: the cleaner deletes what is committed-expired, which is the same set on every
 * instance, and [JobLeaseManager] says why a leased job has to stay correct when it runs twice.
 */
@Singleton
class CleanExpiredInteractiveFlowSessionCron(
    @Inject private val interactiveFlowSessionCleaner: InteractiveFlowSessionCleaner,
    @Inject private val jobLeaseManager: JobLeaseManager
) {
    private val logger = loggerForClass()

    @OptIn(DelicateCoroutinesApi::class)
    @Scheduled(fixedDelay = "15m")
    @Suppress("MaxLineLength")
    fun clean() {
        GlobalScope.launch {
            jobLeaseManager.withLease(CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS) {
                val result = interactiveFlowSessionCleaner.clean()
                if (result.sessionCount > 0) {
                    logger.debug("Cleaned ${result.sessionCount} expired interactive flow sessions (including ${result.authorizationCodeCount} authorization codes, ${result.validationCodesCount} validation codes).")
                }
            }
        }
    }
}

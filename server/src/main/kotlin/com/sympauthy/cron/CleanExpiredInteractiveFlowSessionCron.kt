package com.sympauthy.cron

import com.sympauthy.business.flow.manager.InteractiveFlowSessionCleaner
import com.sympauthy.platform.lock.JobLeaseManager
import com.sympauthy.platform.lock.LeasedJob.CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS
import com.sympauthy.platform.monitoring.loggerForClass
import com.sympauthy.platform.server.BackgroundScope
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.launch

/**
 * Expires the interactive flow sessions whose time is up, and the records attached to them.
 *
 * The lease keeps a deployment of several instances from doing that work several times over; the cleaner
 * stays correct when it runs twice, as `docs/standards/locking-standard.md` requires of a leased job, because
 * it deletes what is committed-expired, which is the same set on every instance.
 */
@Singleton
class CleanExpiredInteractiveFlowSessionCron(
    @Inject private val interactiveFlowSessionCleaner: InteractiveFlowSessionCleaner,
    @Inject private val jobLeaseManager: JobLeaseManager,
    @Inject private val backgroundScope: BackgroundScope
) {
    private val logger = loggerForClass()

    @Scheduled(fixedDelay = "15m")
    fun clean() {
        backgroundScope.launch {
            jobLeaseManager.withLease(CLEAN_EXPIRED_INTERACTIVE_FLOW_SESSIONS) {
                val result = interactiveFlowSessionCleaner.clean()
                if (result.sessionCount > 0) {
                    logger.debug {
                        "Cleaned ${result.sessionCount} expired interactive flow sessions " +
                            "(including ${result.validationCodesCount} validation codes)."
                    }
                }
            }
        }
    }
}

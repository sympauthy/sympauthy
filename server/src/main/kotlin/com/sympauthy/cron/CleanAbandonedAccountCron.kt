package com.sympauthy.cron

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.business.manager.lock.LeasedJob.CLEAN_ABANDONED_ACCOUNTS
import com.sympauthy.business.manager.user.ProvisionalAccountManager
import com.sympauthy.util.loggerForClass
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Collects the accounts an abandoned sign-up left half-created, on a schedule of its own rather than as a
 * step of [CleanExpiredInteractiveFlowSessionCron].
 *
 * The sweep keys on the session being gone rather than on the sessions one run expired, so it needs nothing
 * from the run that removed them and the two are free to overlap: a session expired in the same tick as this
 * one is collected on a later one, which is the lag that design was chosen to tolerate. See
 * [ProvisionalAccountManager.deleteAbandoned].
 *
 * The lease is what keeps a deployment of several instances from doing that work several times over. It is
 * not what keeps it correct: the sweep is written to be run twice, and [JobLeaseManager] says why it has to
 * be.
 */
@Singleton
class CleanAbandonedAccountCron(
    @Inject private val provisionalAccountManager: ProvisionalAccountManager,
    @Inject private val jobLeaseManager: JobLeaseManager
) {
    private val logger = loggerForClass()

    @OptIn(DelicateCoroutinesApi::class)
    @Scheduled(fixedDelay = "15m")
    fun clean() {
        GlobalScope.launch {
            jobLeaseManager.withLease(CLEAN_ABANDONED_ACCOUNTS) {
                val count = provisionalAccountManager.deleteAbandoned()
                if (count > 0) {
                    logger.debug("Cleaned $count accounts left behind by an abandoned sign-up.")
                }
            }
        }
    }
}

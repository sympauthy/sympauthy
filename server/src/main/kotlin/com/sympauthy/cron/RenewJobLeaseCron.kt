package com.sympauthy.cron

import com.sympauthy.business.manager.lock.JobLeaseManager
import com.sympauthy.server.BackgroundScope
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.launch

/**
 * Says every minute that this instance is still alive, by pushing back the leases it holds.
 *
 * It takes no lease of its own, and it is the reason the leased jobs need no duration: a lease stands for
 * as long as this keeps running and is free to be taken shortly after it stops. See
 * [JobLeaseManager.renewLeases].
 *
 * Nothing is logged of a run that succeeds. A run that renews nothing is what an instance running no job
 * looks like, which is most minutes of most days. A run that fails is reported as a leased job's is.
 */
@Singleton
class RenewJobLeaseCron(
    @Inject private val jobLeaseManager: JobLeaseManager,
    @Inject private val backgroundScope: BackgroundScope
) {

    @Scheduled(fixedDelay = JobLeaseManager.RENEWAL_PERIOD)
    fun renew() {
        backgroundScope.launch {
            jobLeaseManager.renewLeases()
        }
    }
}

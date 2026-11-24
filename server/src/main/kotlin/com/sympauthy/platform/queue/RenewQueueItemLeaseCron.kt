package com.sympauthy.platform.queue

import com.sympauthy.platform.lock.JobLeaseManager
import com.sympauthy.platform.queue.manager.QueueDelivery
import com.sympauthy.platform.server.BackgroundScope
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.launch

/**
 * Pushes back the lease on each queue item this instance is attempting, on the period a job's lease is pushed
 * back on, so that an item's lease lapses on how long its instance may go silent, as a job's does. See
 * [QueueDelivery.renewLeases].
 */
@Singleton
class RenewQueueItemLeaseCron(
    @Inject private val queueDelivery: QueueDelivery,
    @Inject private val backgroundScope: BackgroundScope
) {

    @Scheduled(fixedDelay = JobLeaseManager.RENEWAL_PERIOD)
    fun renew() {
        backgroundScope.launch {
            queueDelivery.renewLeases()
        }
    }
}

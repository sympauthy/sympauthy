package com.sympauthy.platform.queue.model

import com.sympauthy.platform.monitoring.model.Outcome

/**
 * How an attempt at a [QueueItem] ended, as its deliverer reads its destination's answer, and so what becomes of
 * the item.
 */
sealed interface DeliveryAttempt {

    /**
     * The destination took the item, which is deleted.
     */
    data object Delivered : DeliveryAttempt

    /**
     * The destination may take the item later, which is retried until it expires.
     */
    data object Retry : DeliveryAttempt

    /**
     * The destination refused the item for good, which is given up. The deliverer counts it under a metric of its
     * own, since the destination answered.
     */
    data object Refused : DeliveryAttempt

    /**
     * The item cannot be delivered, for a reason of this server's own, and is given up, counted on
     * `sympauthy.queue.given_up` under [outcome].
     */
    data class Failed(val outcome: Outcome) : DeliveryAttempt
}

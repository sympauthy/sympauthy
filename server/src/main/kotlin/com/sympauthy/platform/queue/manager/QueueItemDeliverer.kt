package com.sympauthy.platform.queue.manager

import com.sympauthy.platform.queue.model.DeliveryAttempt
import com.sympauthy.platform.queue.model.QueueItem

/**
 * Delivers the items written for one [destination], which [QueueDelivery] hands it one at a time.
 *
 * What an item's payload holds, and how the destination's answer reads as a [DeliveryAttempt], are the deliverer's
 * alone; when an item is attempted, retried, held or given up at its expiry is the queue's.
 */
interface QueueItemDeliverer {

    /**
     * The destination this delivers to, as an item is written for it and the queue's metrics record it.
     */
    val destination: String

    /**
     * Every kind of item this delivers, the set the queue's metrics are recorded under. An item of another kind is
     * one a previous build wrote, and is counted under none.
     */
    val kinds: Set<String>

    /**
     * Whether this delivers at all. The items of a deliverer that does not are attempted by nobody, and given up as
     * they expire.
     */
    val enabled: Boolean

    /**
     * Attempt [item], which this instance holds, and say how the attempt ended.
     *
     * Throws nothing but a cancellation: a failure is answered as the [DeliveryAttempt] it amounts to, and reported
     * by the deliverer where it is a dependency's.
     */
    suspend fun attempt(item: QueueItem): DeliveryAttempt
}

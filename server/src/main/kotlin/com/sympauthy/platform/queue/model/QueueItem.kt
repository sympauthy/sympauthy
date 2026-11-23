package com.sympauthy.platform.queue.model

import java.time.LocalDateTime
import java.util.*

/**
 * An item waiting to be delivered, as its destination's deliverer is handed it to attempt.
 *
 * [destination] names what it is delivered to and [kind] what it is there. [payload] is the JSON document that
 * destination's deliverer wrote, whose shape nothing else knows. [attemptCount] counts the attempts made before the
 * one it is handed for.
 */
data class QueueItem(
    val id: UUID,
    val destination: String,
    val kind: String,
    val payload: String,
    val creationDate: LocalDateTime,
    val expirationDate: LocalDateTime,
    val attemptCount: Int
)

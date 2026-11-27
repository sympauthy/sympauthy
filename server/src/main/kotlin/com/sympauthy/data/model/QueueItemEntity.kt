package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.DataType
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

/**
 * An item waiting to be delivered, from the commit that wrote it until it goes through or is given up.
 *
 * [destination] names what it is delivered to and [kind] what it is there, both as the queue's metrics record them.
 * [payload] is the JSON document the deliverer of that destination wrote, and the shape is that deliverer's alone:
 * it is held as its text, read and written as written.
 *
 * It is written held by nobody and due at once. [holder] and [leaseExpirationDate] are the lease the instance
 * attempting it holds, null between two attempts; [attemptCount] and [nextAttemptDate] are where its retries
 * stand.
 */
@Serdeable
@MappedEntity("queue_items")
class QueueItemEntity(
    val destination: String,
    val kind: String,
    @MappedProperty(type = DataType.JSON)
    val payload: String,
    val creationDate: LocalDateTime,
    val expirationDate: LocalDateTime,
    val attemptCount: Int = 0,
    val nextAttemptDate: LocalDateTime,
    val holder: String? = null,
    val leaseExpirationDate: LocalDateTime? = null
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime

/**
 * The lease on one scheduled job: which instance is running it, since when, and until when the next
 * instance must leave it alone.
 *
 * One row per [com.sympauthy.business.manager.lock.LeasedJob], created by the migration. [holder] and
 * [acquiredAt] are null until an instance has taken it for the first time, and stay at the last holder
 * once a run ends — a released lease is one whose [expirationDate] has passed, not one whose holder was
 * erased.
 *
 * **Both timestamps are read off the database's clock rather than written from an instance's**, which is
 * the only way two of them compare against one timeline. They are in whatever zone that database answers
 * `LOCALTIMESTAMP` in, where every other timestamp in this schema is UTC because the application writing
 * it forces its own zone to UTC: a deployment whose database runs in another zone has these two columns
 * in that zone, and nothing compares them against a column of another table.
 */
@Serdeable
@MappedEntity("job_leases")
data class JobLeaseEntity(
    val holder: String?,
    val acquiredAt: LocalDateTime?,
    val expirationDate: LocalDateTime
) {

    @Id
    var name: String? = null
}

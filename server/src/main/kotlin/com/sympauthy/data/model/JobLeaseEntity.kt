package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime

/**
 * The lease on one scheduled job: which instance is running it, since when, and until when the next
 * instance must leave it alone.
 *
 * One row per [com.sympauthy.business.manager.lock.ScheduledJob], created by the migration. [holder] and
 * [acquiredAt] are null until an instance has taken it for the first time, and stay at the last holder
 * once a run ends — a released lease is one whose [expirationDate] has passed, not one whose holder was
 * erased.
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

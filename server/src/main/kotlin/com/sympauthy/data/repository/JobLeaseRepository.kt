package com.sympauthy.data.repository

import com.sympauthy.data.model.JobLeaseEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime

/**
 * Repository for [JobLeaseEntity], the lease one instance takes over a scheduled job so that the others
 * skip that tick.
 *
 * Both statements are single updates that stand on their own. Neither belongs to the transaction of the
 * job it guards — a run is minutes of work and its own writes, and a lease held open across it would be
 * one transaction over all of them.
 */
interface JobLeaseRepository : CoroutineCrudRepository<JobLeaseEntity, String> {

    /**
     * Take the lease [name] until [expiresAt] on behalf of [holder], and answer 1 when this instance got
     * it and 0 when another one holds it.
     *
     * One statement, so the answer does not depend on the isolation level: a second instance blocks on the
     * row, re-reads it as the winner left it, and matches nothing.
     */
    @Query(
        """
        UPDATE job_leases
        SET holder = :holder, acquired_at = :now, expiration_date = :expiresAt
        WHERE name = :name AND expiration_date <= :now
        """
    )
    suspend fun acquire(name: String, holder: String, now: LocalDateTime, expiresAt: LocalDateTime): Int

    /**
     * End [holder]'s lease on [name], leaving it free from [now], and answer how many rows that was.
     *
     * Keyed on the holder as well as the name: an instance whose lease expired under it while its run was
     * still going has already been replaced, and must not free the lease its successor is holding.
     */
    @Query("UPDATE job_leases SET expiration_date = :now WHERE name = :name AND holder = :holder")
    suspend fun release(name: String, holder: String, now: LocalDateTime): Int
}

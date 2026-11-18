package com.sympauthy.data.repository

import com.sympauthy.data.model.JobLeaseEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime

/**
 * Repository for [JobLeaseEntity], the lease one instance takes over a scheduled job so that the others
 * skip that tick.
 *
 * Every statement is a single update that stands on its own, outside the transaction of the job it
 * guards, as `docs/standards/locking-standard.md` requires of a lease.
 */
interface JobLeaseRepository : CoroutineCrudRepository<JobLeaseEntity, String> {

    /**
     * Answer what time the database thinks it is: the clock every lease is timed by, read back so the
     * duration is added in Kotlin, as `docs/standards/locking-standard.md` requires.
     */
    @Query("SELECT LOCALTIMESTAMP")
    suspend fun now(): LocalDateTime

    /**
     * Take the lease [name] until [expiresAt] on behalf of [holder], and answer 1 when this instance got
     * it and 0 when another one holds it. [now] and [expiresAt] are read off [now], never off this
     * instance's clock.
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
     * Push back to [expiresAt] every lease [holder] is still holding, and answer how many there were.
     *
     * One statement for all of them, because the caller is an instance rather than a job: what it has to
     * say is that it is still alive, and every lease it holds hears the same thing. A lease already past
     * its expiry is passed over, as `docs/standards/locking-standard.md` requires.
     */
    @Query(
        """
        UPDATE job_leases
        SET expiration_date = :expiresAt
        WHERE holder = :holder AND expiration_date > :now
        """
    )
    suspend fun renew(holder: String, now: LocalDateTime, expiresAt: LocalDateTime): Int

    /**
     * End [holder]'s lease on [name], leaving it free from now, and answer how many rows that was.
     *
     * It takes the clock itself rather than being handed one: there is nothing to add to it here, so the
     * statement can name `LOCALTIMESTAMP` and spare the caller a read. Keyed on the holder as well as the
     * name, so a lease taken over by a successor is not freed.
     */
    @Query("UPDATE job_leases SET expiration_date = LOCALTIMESTAMP WHERE name = :name AND holder = :holder")
    suspend fun release(name: String, holder: String): Int
}

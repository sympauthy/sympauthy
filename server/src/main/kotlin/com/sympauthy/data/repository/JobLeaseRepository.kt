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
     * Answer what time the database thinks it is.
     *
     * It is read rather than written into the statements below because the arithmetic is not portable:
     * every dialect spells `LOCALTIMESTAMP`, none of them spells adding a bound number of minutes to it
     * the same way. So the clock comes back here and the duration is added in Kotlin, which costs one
     * round trip every quarter of an hour and buys every instance comparing against one clock.
     */
    @Query("SELECT LOCALTIMESTAMP")
    suspend fun now(): LocalDateTime

    /**
     * Take the lease [name] until [expiresAt] on behalf of [holder], and answer 1 when this instance got
     * it and 0 when another one holds it.
     *
     * [now] and [expiresAt] are read off [now] rather than off this instance's clock, so an instance
     * running fast cannot hold a lease past its duration and one running slow cannot take a live one.
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
     * say is that it is still alive, and every lease it holds hears the same thing.
     *
     * Guarded on the expiry as well as the holder. A lease of this holder's that has already passed its
     * expiry may have been taken by another instance between the two statements, and pulling it back
     * would take a job away from the instance now running it.
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
     * statement can name `LOCALTIMESTAMP` and spare the caller a read.
     *
     * Keyed on the holder as well as the name: an instance whose lease expired under it while its run was
     * still going has already been replaced, and must not free the lease its successor is holding.
     */
    @Query("UPDATE job_leases SET expiration_date = LOCALTIMESTAMP WHERE name = :name AND holder = :holder")
    suspend fun release(name: String, holder: String): Int
}

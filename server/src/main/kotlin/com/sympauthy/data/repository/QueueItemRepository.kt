package com.sympauthy.data.repository

import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.data.model.QueueItemSummaryEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * Repository for [QueueItemEntity], the items waiting to be delivered.
 *
 * An item is held under a lease while an instance attempts it, as `docs/standards/locking-standard.md` has a work
 * row leased across transactions: every statement but [claimDue] stands on its own, and every one that ends an
 * attempt names its holder, so an instance whose lease lapsed and was taken elsewhere changes nothing.
 */
interface QueueItemRepository : CoroutineCrudRepository<QueueItemEntity, UUID> {

    /**
     * Answer what time the database thinks it is: the clock every lease and every retry is timed by.
     */
    @Query("SELECT LOCALTIMESTAMP")
    suspend fun now(): LocalDateTime

    /**
     * Take the item [id] on behalf of [holder] until [expiresAt], where it was never attempted and nobody holds
     * it, and answer 1 when this instance got it.
     *
     * It is what the instance that committed an item attempts it through, so it asks nothing of the item's due
     * date: one written by this instance's clock may read as not yet due by the database's.
     */
    @Query(
        """
        UPDATE queue_items
        SET holder = :holder, lease_expiration_date = :expiresAt
        WHERE id = :id AND holder IS NULL AND attempt_count = 0
        """
    )
    suspend fun takeUnattempted(id: UUID, holder: String, expiresAt: LocalDateTime): Int

    /**
     * Claim, oldest due first, up to [limit] items for [destination] due by [now] that nobody holds or whose
     * holder's lease has lapsed. A claim, so it runs inside the transaction [holdClaimed] writes the lease in.
     *
     * It takes the items of one destination, so an instance claims none it cannot deliver: one of a build that came
     * before the destination did.
     */
    @Query(
        """
        SELECT * FROM queue_items
        WHERE destination = :destination
          AND next_attempt_date <= :now AND (holder IS NULL OR lease_expiration_date <= :now)
        ORDER BY next_attempt_date
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """
    )
    suspend fun claimDue(destination: String, now: LocalDateTime, limit: Int): List<QueueItemEntity>

    /**
     * Hold the items [ids] [claimDue] claimed on behalf of [holder] until [expiresAt], re-asserting what the claim
     * selected them by.
     */
    @Query(
        """
        UPDATE queue_items
        SET holder = :holder, lease_expiration_date = :expiresAt
        WHERE id IN (:ids) AND next_attempt_date <= :now AND (holder IS NULL OR lease_expiration_date <= :now)
        """
    )
    suspend fun holdClaimed(ids: List<UUID>, holder: String, now: LocalDateTime, expiresAt: LocalDateTime): Int

    /**
     * Push back to [expiresAt] the lease [holder] still holds on each of the items [ids], and answer how many there
     * were. A lease already past its expiry is passed over, as a job's is.
     */
    @Query(
        """
        UPDATE queue_items
        SET lease_expiration_date = :expiresAt
        WHERE id IN (:ids) AND holder = :holder AND lease_expiration_date > :now
        """
    )
    suspend fun renew(ids: List<UUID>, holder: String, now: LocalDateTime, expiresAt: LocalDateTime): Int

    /**
     * Leave the item [id] [holder] attempted for a retry, as its [attemptCount]th attempt, due at
     * [nextAttemptDate] and held by nobody.
     */
    @Query(
        """
        UPDATE queue_items
        SET attempt_count = :attemptCount, next_attempt_date = :nextAttemptDate,
            holder = NULL, lease_expiration_date = NULL
        WHERE id = :id AND holder = :holder
        """
    )
    suspend fun reschedule(id: UUID, holder: String, attemptCount: Int, nextAttemptDate: LocalDateTime): Int

    /**
     * Hand back the item [id] [holder] holds, as it stands and due as it was, and answer 1 where it still held it.
     */
    @Query("UPDATE queue_items SET holder = NULL, lease_expiration_date = NULL WHERE id = :id AND holder = :holder")
    suspend fun releaseHeld(id: UUID, holder: String): Int

    /**
     * Hand back every item [holder] still holds, as it stands, and answer how many there were.
     */
    @Query("UPDATE queue_items SET holder = NULL, lease_expiration_date = NULL WHERE holder = :holder")
    suspend fun release(holder: String): Int

    /**
     * Remove the item [id] [holder] delivered or gave up, and answer 1 where it still held it.
     */
    @Query("DELETE FROM queue_items WHERE id = :id AND holder = :holder")
    suspend fun deleteHeld(id: UUID, holder: String): Int

    /**
     * How many items wait, and since when the oldest has, by the destination and the kind of each.
     */
    @Query(
        """
        SELECT destination, kind, COUNT(*) AS pending_count, MIN(creation_date) AS oldest_creation_date
        FROM queue_items
        GROUP BY destination, kind
        """
    )
    suspend fun summarize(): List<QueueItemSummaryEntity>
}

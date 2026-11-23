package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.bean
import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.data.withFixture
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/**
 * The queue item, whose `payload` is a `json` column held as its text, and whose lease is written by raw statements
 * every dialect has to read the same way.
 *
 * Every item is written for a destination of this class's own, which the claim and the summary are asked about, so
 * that a neighbour's item is neither claimed nor counted.
 */
class QueueItemRepositoryTest {

    private val destination = "queue-item-repository-test"
    private val holder = "queue-item-repository-test-holder"
    private val otherHolder = "queue-item-repository-test-other-holder"
    private val longAgo = BASE_DATE.minusYears(50)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the payload as the text it was written as`(database: Database) = withFixture(database) {
        val items = repository<QueueItemRepository>()
        val payload = """{"receiver":"ada@example.org","parameters":{"code":"123456"}}"""
        val id = saveItem(payload = payload)

        val stored = items.findById(id)

        assertNotNull(stored)
        assertEquals(payload, stored!!.payload)
        assertEquals(destination, stored.destination)
        assertEquals("claim_validation", stored.kind)
        assertEquals(BASE_DATE, stored.creationDate)
        // Written held by nobody, never attempted, and due as soon as it was written.
        assertNull(stored.holder)
        assertEquals(0, stored.attemptCount)
        assertEquals(BASE_DATE, stored.nextAttemptDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `takeUnattempted - Takes an item nobody holds and nobody attempted, once`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            val id = saveItem()

            assertEquals(1, items.takeUnattempted(id, holder, BASE_DATE.plusMinutes(3)))
            assertEquals(0, items.takeUnattempted(id, otherHolder, BASE_DATE.plusMinutes(3)))

            val stored = items.findById(id)!!
            assertEquals(holder, stored.holder)
            assertEquals(BASE_DATE.plusMinutes(3), stored.leaseExpirationDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `takeUnattempted - Leaves an item already attempted`(database: Database) = withFixture(database) {
        val items = repository<QueueItemRepository>()
        val id = saveItem(attemptCount = 1)

        assertEquals(0, items.takeUnattempted(id, holder, BASE_DATE.plusMinutes(3)))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `claimDue - Takes what is due and free, and what a lapsed lease holds`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            val due = saveItem(nextAttemptDate = longAgo)
            val lapsed = saveItem(nextAttemptDate = longAgo, holder = otherHolder, leaseExpirationDate = longAgo)
            val notYetDue = saveItem(nextAttemptDate = longAgo.plusDays(2))
            val held = saveItem(
                nextAttemptDate = longAgo, holder = otherHolder, leaseExpirationDate = longAgo.plusDays(2)
            )

            val claimed = claimedIds(items, now = longAgo.plusDays(1))

            assertTrue(due in claimed, "A due item nobody held was not claimed.")
            assertTrue(lapsed in claimed, "An item whose holder's lease lapsed was not claimed.")
            assertFalse(notYetDue in claimed, "An item not yet due was claimed.")
            assertFalse(held in claimed, "An item another instance holds was claimed.")
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `claimDue - Leaves the items of another destination`(database: Database) = withFixture(database) {
        val items = repository<QueueItemRepository>()
        val elsewhere = saveItem(destination = "$destination-elsewhere", nextAttemptDate = longAgo)

        val claimed = claimedIds(items, now = longAgo.plusDays(1))

        assertFalse(elsewhere in claimed, "An item of another destination was claimed.")
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `claimDue - Takes the oldest due first, and no more than it asked for`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            // Further back than any other case of this class, so that these two are the oldest due.
            val older = saveItem(nextAttemptDate = longAgo.minusYears(10))
            saveItem(nextAttemptDate = longAgo.minusYears(9))

            val claimed = items.claimDue(destination, longAgo.minusYears(9), limit = 1)

            assertEquals(listOf(older), claimed.map { it.id })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `claimExpired - Takes what expired and is free, and what a lapsed lease holds`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            val expired = saveItem(nextAttemptDate = longAgo, expirationDate = longAgo)
            val lapsed = saveItem(
                nextAttemptDate = longAgo, expirationDate = longAgo, holder = otherHolder, leaseExpirationDate = longAgo
            )
            val unexpired = saveItem(nextAttemptDate = longAgo, expirationDate = longAgo.plusDays(2))
            val held = saveItem(
                nextAttemptDate = longAgo,
                expirationDate = longAgo,
                holder = otherHolder,
                leaseExpirationDate = longAgo.plusDays(2)
            )
            val elsewhere = saveItem(
                destination = "$destination-elsewhere", nextAttemptDate = longAgo, expirationDate = longAgo
            )

            val claimed = items.claimExpired(destination, longAgo.plusDays(1), limit = 1_000).map { it.id }

            assertTrue(expired in claimed, "An expired item nobody held was not claimed.")
            assertTrue(lapsed in claimed, "An expired item whose holder's lease lapsed was not claimed.")
            assertFalse(unexpired in claimed, "An item not yet expired was claimed.")
            assertFalse(held in claimed, "An item another instance holds was claimed.")
            assertFalse(elsewhere in claimed, "An item of another destination was claimed.")
        }

    /**
     * `FOR UPDATE SKIP LOCKED` is what lets two instances poll at once without either waiting, so the other poll
     * is driven over a connection of its own and its transaction stays open.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `claimDue - Skips what another poll is claiming rather than waiting for it`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            val claimedElsewhere = saveItem(nextAttemptDate = longAgo)
            val free = saveItem(nextAttemptDate = longAgo)
            val otherPoll = database.bean<ConnectionFactory>().create().awaitFirst()

            try {
                otherPoll.hold(claimedElsewhere)

                val claimed = claimedIds(items, now = longAgo.plusDays(1))

                assertFalse(claimedElsewhere in claimed, "An item another poll was claiming was claimed anyway.")
                assertTrue(free in claimed, "An item nobody claimed was skipped, so nothing was claimed at all.")
            } finally {
                otherPoll.rollbackTransaction().awaitFirstOrNull()
                otherPoll.close().awaitFirstOrNull()
            }
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `holdClaimed - Holds what is still free, and leaves what another instance took meanwhile`(
        database: Database
    ) = withFixture(database) {
        val items = repository<QueueItemRepository>()
        val free = saveItem(nextAttemptDate = longAgo)
        val takenMeanwhile = saveItem(
            nextAttemptDate = longAgo, holder = otherHolder, leaseExpirationDate = longAgo.plusDays(2)
        )

        val held = items.holdClaimed(listOf(free, takenMeanwhile), holder, longAgo.plusDays(1), BASE_DATE)

        assertEquals(1, held)
        assertEquals(holder, items.findById(free)!!.holder)
        assertEquals(otherHolder, items.findById(takenMeanwhile)!!.holder)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `renew - Pushes back the leases its holder still holds on the items named, and passes over a lapsed one`(
        database: Database
    ) = withFixture(database) {
        val items = repository<QueueItemRepository>()
        val live = saveItem(holder = holder, leaseExpirationDate = BASE_DATE.plusMinutes(1))
        val lapsed = saveItem(holder = holder, leaseExpirationDate = BASE_DATE.minusMinutes(1))
        val unnamed = saveItem(holder = holder, leaseExpirationDate = BASE_DATE.plusMinutes(1))
        val others = saveItem(holder = otherHolder, leaseExpirationDate = BASE_DATE.plusMinutes(1))

        assertEquals(1, items.renew(listOf(live, lapsed, others), holder, BASE_DATE, BASE_DATE.plusMinutes(3)))

        assertEquals(BASE_DATE.plusMinutes(3), items.findById(live)!!.leaseExpirationDate)
        assertEquals(BASE_DATE.minusMinutes(1), items.findById(lapsed)!!.leaseExpirationDate)
        assertEquals(BASE_DATE.plusMinutes(1), items.findById(unnamed)!!.leaseExpirationDate)
        assertEquals(BASE_DATE.plusMinutes(1), items.findById(others)!!.leaseExpirationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `releaseHeld - Hands back the item its holder holds, as it stands, and only that one`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            val mine = saveItem(holder = holder, leaseExpirationDate = BASE_DATE.plusMinutes(3), attemptCount = 1)
            val others = saveItem(holder = otherHolder, leaseExpirationDate = BASE_DATE.plusMinutes(3))

            assertEquals(1, items.releaseHeld(mine, holder))
            assertEquals(0, items.releaseHeld(others, holder))

            val released = items.findById(mine)!!
            assertNull(released.holder)
            assertNull(released.leaseExpirationDate)
            assertEquals(1, released.attemptCount)
            assertEquals(otherHolder, items.findById(others)!!.holder)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `reschedule - Leaves the item for its next attempt, held by nobody`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            val id = saveItem(holder = holder, leaseExpirationDate = BASE_DATE.plusMinutes(3))

            assertEquals(1, items.reschedule(id, holder, 2, BASE_DATE.plusSeconds(30)))

            val stored = items.findById(id)!!
            assertEquals(2, stored.attemptCount)
            assertEquals(BASE_DATE.plusSeconds(30), stored.nextAttemptDate)
            assertNull(stored.holder)
            assertNull(stored.leaseExpirationDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `reschedule - Changes nothing of an item its holder lost`(database: Database) = withFixture(database) {
        val items = repository<QueueItemRepository>()
        val id = saveItem(holder = otherHolder, leaseExpirationDate = BASE_DATE.plusMinutes(3))

        assertEquals(0, items.reschedule(id, holder, 2, BASE_DATE.plusSeconds(30)))

        assertEquals(otherHolder, items.findById(id)!!.holder)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `release - Hands back every item its holder holds, as it stands`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            val mine = saveItem(holder = holder, leaseExpirationDate = BASE_DATE.plusMinutes(3), attemptCount = 1)
            val others = saveItem(holder = otherHolder, leaseExpirationDate = BASE_DATE.plusMinutes(3))

            assertEquals(1, items.release(holder))

            val released = items.findById(mine)!!
            assertNull(released.holder)
            assertNull(released.leaseExpirationDate)
            assertEquals(1, released.attemptCount)
            assertEquals(otherHolder, items.findById(others)!!.holder)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteHeld - Removes the item its holder holds, and only that one`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            val mine = saveItem(holder = holder, leaseExpirationDate = BASE_DATE.plusMinutes(3))
            val others = saveItem(holder = otherHolder, leaseExpirationDate = BASE_DATE.plusMinutes(3))

            assertEquals(1, items.deleteHeld(mine, holder))
            assertEquals(0, items.deleteHeld(others, holder))

            assertNull(items.findById(mine))
            assertNotNull(items.findById(others))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `summarize - Counts what waits by destination and kind, and dates the oldest`(database: Database) =
        withFixture(database) {
            val items = repository<QueueItemRepository>()
            saveItem(creationDate = BASE_DATE.minusMinutes(5))
            saveItem(creationDate = BASE_DATE)

            val summary = items.summarize().single { it.destination == destination }

            assertEquals("claim_validation", summary.kind)
            assertEquals(2, summary.pendingCount)
            assertEquals(BASE_DATE.minusMinutes(5), summary.oldestCreationDate)
        }

    private suspend fun claimedIds(items: QueueItemRepository, now: LocalDateTime): List<UUID?> =
        items.claimDue(destination, now, limit = 1_000).map { it.id }

    /** Opens a transaction on this connection and holds [id] in it, leaving both open. */
    private suspend fun Connection.hold(id: UUID) {
        beginTransaction().awaitFirstOrNull()
        createStatement("SELECT id FROM queue_items WHERE id = '$id' FOR UPDATE")
            .execute()
            .awaitFirst()
            .map { row, _ -> row.get(0) }
            .awaitFirstOrNull()
    }

    @Suppress("LongParameterList")
    private suspend fun RepositoryFixture.saveItem(
        payload: String = """{"code":"123456"}""",
        destination: String = this@QueueItemRepositoryTest.destination,
        creationDate: LocalDateTime = BASE_DATE,
        nextAttemptDate: LocalDateTime = creationDate,
        expirationDate: LocalDateTime = BASE_DATE.plusYears(100),
        attemptCount: Int = 0,
        holder: String? = null,
        leaseExpirationDate: LocalDateTime? = null
    ): UUID {
        val items = repository<QueueItemRepository>()
        return items.save(
            QueueItemEntity(
                destination = destination,
                kind = "claim_validation",
                payload = payload,
                creationDate = creationDate,
                expirationDate = expirationDate,
                attemptCount = attemptCount,
                nextAttemptDate = nextAttemptDate,
                holder = holder,
                leaseExpirationDate = leaseExpirationDate
            )
        ).id!!.also { id -> deleteOnEnd { items.deleteById(id) } }
    }
}

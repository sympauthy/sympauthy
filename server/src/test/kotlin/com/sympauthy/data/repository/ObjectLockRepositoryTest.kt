package com.sympauthy.data.repository

import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.data.Database
import com.sympauthy.data.bean
import com.sympauthy.data.withFixture
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The stripes a named lock is taken over, and the one thing about them that is not in the Kotlin: that
 * `SELECT … FOR UPDATE` makes a second caller wait.
 *
 * A test where both transactions run one after the other would assert a property that held before the
 * query existed, so the holder here is driven over a connection of its own and its transaction stays
 * open while the second caller tries. It names [STRIPE_COUNT] across the layer boundary on purpose: the
 * constant and the rows the migration inserts are one fact, and this is where the two meet a database.
 */
class ObjectLockRepositoryTest {

    private val heldStripe = 17
    private val freeStripe = 42

    /**
     * Long enough that a second caller returning inside it means it never waited, and well under the two
     * seconds H2 waits for a row before giving up.
     */
    private val hold = 400L

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `The migration seeds one row per stripe`(database: Database) = withFixture(database) {
        assertEquals(LockKey.STRIPE_COUNT.toLong(), repository<ObjectLockRepository>().count())
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `lock - Answers the stripe it took`(database: Database) = withFixture(database) {
        assertEquals(heldStripe, repository<ObjectLockRepository>().lock(heldStripe))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `lock - Waits for the transaction holding that stripe, and for no other`(database: Database) =
        withFixture(database) {
            val locks = repository<ObjectLockRepository>()
            val holder = database.bean<ConnectionFactory>().create().awaitFirst()

            try {
                holder.hold(heldStripe)

                coroutineScope {
                    val waiting = async(Dispatchers.IO) { locks.lock(heldStripe) }
                    val elsewhere = async(Dispatchers.IO) { locks.lock(freeStripe) }
                    withContext(Dispatchers.IO) { delay(hold) }

                    assertFalse(waiting.isCompleted, "The lock was handed out while another transaction held it.")
                    assertTrue(elsewhere.isCompleted, "A free stripe waited, so the wait was not about the stripe.")

                    holder.rollbackTransaction().awaitFirstOrNull()
                    assertEquals(heldStripe, waiting.await())
                }
            } finally {
                holder.close().awaitFirstOrNull()
            }
        }

    /** Opens a transaction on this connection and takes [stripe] in it, leaving both open. */
    private suspend fun Connection.hold(stripe: Int) {
        beginTransaction().awaitFirstOrNull()
        createStatement("SELECT id FROM object_locks WHERE id = $stripe FOR UPDATE")
            .execute()
            .awaitFirst()
            .map { row, _ -> row.get(0) }
            .awaitFirstOrNull()
    }
}

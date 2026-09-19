package com.sympauthy.business.manager.lock

import com.sympauthy.data.Database
import com.sympauthy.data.bean
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The record of what a transaction holds: that it outlives the block, and that it does not outlive the
 * transaction.
 *
 * Nothing smaller can show either. Which calls share a connection is the container's decision, and a
 * record that outlived its transaction is invisible from the outside — the next transaction is handed a
 * connection status of its own, so it takes its locks and answers correctly whether or not the one
 * before it was ever released. The map is read directly for that reason.
 */
class HeldStripesTest {

    /** Stripe 24 and 26 respectively. */
    private val key = LockKey.IdentifierValue("held-stripes-test")
    private val otherKey = LockKey.IdentifierValue("held-stripes-test-other")

    @Test
    fun `held - Answers the stripes the transaction took, from outside the block that took them`() =
        runTest {
            assertEquals(listOf(24, 26), Database.H2.bean<LockAndSettle>().committing(key, otherKey))
        }

    @Test
    fun `held - Answers nothing outside a transaction`() = runTest {
        assertEquals(emptyList<Int>(), HeldStripes().held())
    }

    @Test
    fun `hold - Releases the record when the transaction commits`() = runTest {
        val heldStripes = Database.H2.bean<HeldStripes>()

        Database.H2.bean<LockAndSettle>().committing(key, otherKey)

        assertEquals(emptyMap<Any, List<Int>>(), heldStripes.byConnection)
    }

    @Test
    fun `hold - Releases the record when the transaction rolls back`() = runTest {
        val heldStripes = Database.H2.bean<HeldStripes>()

        assertThrows<IllegalStateException> { Database.H2.bean<LockAndSettle>().rollingBack(key) }

        assertEquals(emptyMap<Any, List<Int>>(), heldStripes.byConnection)
    }
}

/**
 * A transaction that takes a lock and then ends, either way it can end.
 *
 * It is a bean rather than a method of the test because nothing a test body does opens a transaction,
 * and the transaction is the whole subject.
 */
@Singleton
open class LockAndSettle(
    @Inject private val lockManager: LockManager,
    @Inject private val heldStripes: HeldStripes
) {

    /** Takes both keys and answers what the record held once the block had returned. */
    @Transactional
    open suspend fun committing(key: LockKey, otherKey: LockKey): List<Int> {
        lockManager.withLock(key, otherKey) { }
        return heldStripes.held()
    }

    @Transactional
    open suspend fun rollingBack(key: LockKey) {
        lockManager.withLock(key) { }
        error("the transaction failed")
    }
}

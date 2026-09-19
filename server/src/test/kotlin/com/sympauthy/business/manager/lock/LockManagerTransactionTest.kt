package com.sympauthy.business.manager.lock

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.bean
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.withFixture
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * What [LockManager.withLock] does that only a real transaction can show, which is everything about it
 * that compiles either way.
 *
 * A lock released by the autocommit that took it is no lock, and a generic suspending method taking a
 * vararg and a block is an unusual enough shape to intercept that it is worth an assertion rather than
 * an assumption. The manager is resolved from a context rather than constructed, because the proxy is
 * the subject; a MockK double would prove the opposite of what is wanted here.
 *
 * The transaction is observed by what it undoes: a row written inside a failing block survives an
 * autocommit and does not survive a rollback. That the lock is then held for the length of that
 * transaction is [com.sympauthy.data.repository.ObjectLockRepositoryTest]'s.
 *
 * The second and the third case are the ones a double cannot reach at all: which calls share a
 * transaction is decided by the container, and the rule they hold is that the stripes a transaction took
 * outlive the block that took them.
 */
class LockManagerTransactionTest {

    private val status = "lock-manager-transaction-test"

    /** Stripe 50 and 11 respectively, which is what makes one of them cover neither the other nor both. */
    private val first = LockKey.IdentifierValue("$status-first")
    private val second = LockKey.IdentifierValue("$status-second")

    @Test
    fun `withLock - Runs the block in a transaction, so a failure leaves nothing behind`() =
        withFixture(Database.H2) {
            val manager = database.bean<LockManager>()
            val users = repository<UserRepository>()
            var written: UserEntity? = null
            deleteOnEnd { written?.id?.let { users.deleteById(it) } }

            assertThrows<IllegalStateException> {
                manager.withLock(LockKey.IdentifierValue(status)) {
                    written = users.save(UserEntity(status = status, creationDate = BASE_DATE, sessionId = null))
                    error("the block failed")
                }
            }

            assertEquals(emptyList<UserEntity>(), users.findByStatusAndSessionIdIsNull(status).toList())
        }

    @Test
    fun `withLock - Refuses a second lock in the transaction still holding the first`() =
        withFixture(Database.H2) {
            val thrown = assertThrows<BusinessException> {
                database.bean<SequentialLocks>().inOneTransaction(first, second)
            }

            assertEquals("lock.second", thrown.detailsId)
        }

    @Test
    fun `withLock - Takes a lock in each of two transactions of its own`() =
        withFixture(Database.H2) {
            database.bean<SequentialLocks>().inATransactionEach(first, second)
        }

    @Test
    fun `withLock - Takes nothing again for a second lock the transaction already covers`() =
        withFixture(Database.H2) {
            val held = database.bean<SequentialLocks>().aSetThenASubsetOfIt(first, second)

            assertEquals(held.distinct(), held, "A stripe the transaction already held was taken again.")
        }

    @Test
    fun `withLock - Takes a lock after one that named no key at all`() =
        withFixture(Database.H2) {
            database.bean<SequentialLocks>().nothingThenASet(first)
        }
}

/**
 * A caller taking two locks one after the other, once inside a transaction it opened over both and once
 * with none of its own.
 *
 * It is a bean rather than a method of the test because what is under test is which transaction the
 * container puts the two calls in, and nothing a test body does opens one.
 */
@Singleton
open class SequentialLocks(
    @Inject private val lockManager: LockManager,
    @Inject private val heldStripes: HeldStripes
) {

    @Transactional
    open suspend fun inOneTransaction(first: LockKey, second: LockKey) {
        lockManager.withLock(first) { }
        lockManager.withLock(second) { }
    }

    suspend fun inATransactionEach(first: LockKey, second: LockKey) {
        lockManager.withLock(first) { }
        lockManager.withLock(second) { }
    }

    /** Answers what the transaction holds once both calls have returned. */
    @Transactional
    open suspend fun aSetThenASubsetOfIt(first: LockKey, second: LockKey): List<Int> {
        lockManager.withLock(first, second) { }
        return lockManager.withLock(second) { heldStripes.held() }
    }

    @Transactional
    open suspend fun nothingThenASet(key: LockKey) {
        lockManager.withLock { }
        lockManager.withLock(key) { }
    }
}

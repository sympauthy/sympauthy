package com.sympauthy.business.manager.lock

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.bean
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * That `@Transactional` reaches [LockManager.withLock] at all, which is the one thing about it that
 * compiles either way.
 *
 * A lock released by the autocommit that took it is no lock, and a generic suspending method taking a
 * vararg and a block is an unusual enough shape to intercept that it is worth an assertion rather than
 * an assumption. The manager is resolved from a context rather than constructed, because the proxy is
 * the subject; a MockK double would prove the opposite of what is wanted here.
 *
 * The transaction is observed by what it undoes: a row written inside a failing block survives an
 * autocommit and does not survive a rollback. That the lock is then held for the length of that
 * transaction is [com.sympauthy.data.repository.ObjectLockRepositoryTest]'s.
 */
class LockManagerTransactionTest {

    private val status = "lock-manager-transaction-test"

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

            assertNull(written?.id?.let { users.findById(it) })
        }
}

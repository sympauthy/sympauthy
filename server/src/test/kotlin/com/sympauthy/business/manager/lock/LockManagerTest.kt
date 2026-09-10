package com.sympauthy.business.manager.lock

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.data.repository.ObjectLockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class LockManagerTest {

    @MockK
    lateinit var objectLockRepository: ObjectLockRepository

    @InjectMockKs
    lateinit var manager: LockManager

    /** Stripe 11, 13 and 63 respectively, which `LockKeyTest` is what holds. */
    private val user = LockKey.User(UUID.fromString("00000000-0000-0000-0000-000000000000"))
    private val identifier = LockKey.IdentifierValue("someone@example.com")
    private val otherUser = LockKey.User(UUID.fromString("6f3d9a12-4c5e-4b3a-8f21-0d7e5b9c1a44"))

    @Test
    fun `withLock - Takes the stripes in ascending order, whatever order the keys came in`() = runTest {
        coEvery { objectLockRepository.lock(any()) } returns 0

        manager.withLock(otherUser, identifier, user) { }

        coVerifyOrder {
            objectLockRepository.lock(11)
            objectLockRepository.lock(13)
            objectLockRepository.lock(63)
        }
    }

    @Test
    fun `withLock - Takes one stripe once when two keys share it`() = runTest {
        coEvery { objectLockRepository.lock(any()) } returns 0

        manager.withLock(identifier, LockKey.IdentifierValue("someone@example.com")) { }

        coVerify(exactly = 1) { objectLockRepository.lock(13) }
    }

    @Test
    fun `withLock - Runs the block once and answers what it answered`() = runTest {
        coEvery { objectLockRepository.lock(any()) } returns 0
        var runs = 0

        val answer = manager.withLock(user) {
            runs++
            "answered"
        }

        assertEquals("answered", answer)
        assertEquals(1, runs)
    }

    @Test
    fun `withLock - Runs a nested block without taking anything the outer call already holds`() = runTest {
        coEvery { objectLockRepository.lock(any()) } returns 0
        var ran = false

        manager.withLock(user, identifier) {
            manager.withLock(identifier) { ran = true }
        }

        assertTrue(ran)
        coVerify(exactly = 1) { objectLockRepository.lock(13) }
    }

    @Test
    fun `withLock - Refuses a nested lock the outer call does not cover`() = runTest {
        coEvery { objectLockRepository.lock(any()) } returns 0
        var ran = false

        val thrown = assertThrows<BusinessException> {
            manager.withLock(user) {
                manager.withLock(otherUser) { ran = true }
            }
        }

        assertEquals("lock.nested", thrown.detailsId)
        assertFalse(ran)
        coVerify(exactly = 0) { objectLockRepository.lock(63) }
    }
}

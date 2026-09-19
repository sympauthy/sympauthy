package com.sympauthy.business.manager.lock

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.data.repository.ObjectLockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
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

    @MockK
    lateinit var heldStripes: HeldStripes

    @InjectMockKs
    lateinit var manager: LockManager

    /** Stripe 11, 13 and 63 respectively, which `LockKeyTest` is what holds. */
    private val user = LockKey.User(UUID.fromString("00000000-0000-0000-0000-000000000000"))
    private val identifier = LockKey.IdentifierValue("someone@example.com")
    private val otherUser = LockKey.User(UUID.fromString("6f3d9a12-4c5e-4b3a-8f21-0d7e5b9c1a44"))

    @Test
    fun `withLock - Takes the stripes in ascending order, whatever order the keys came in`() = runTest {
        coEvery { heldStripes.held() } returns emptyList()
        coEvery { heldStripes.hold(any()) } just runs
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
        coEvery { heldStripes.held() } returns emptyList()
        coEvery { heldStripes.hold(any()) } just runs
        coEvery { objectLockRepository.lock(any()) } returns 0

        manager.withLock(identifier, LockKey.IdentifierValue("someone@example.com")) { }

        coVerify(exactly = 1) { objectLockRepository.lock(13) }
    }

    @Test
    fun `withLock - Records each stripe against the transaction as it takes it`() = runTest {
        coEvery { heldStripes.held() } returns emptyList()
        coEvery { heldStripes.hold(any()) } just runs
        coEvery { objectLockRepository.lock(any()) } returns 0

        manager.withLock(otherUser, user) { }

        coVerifyOrder {
            objectLockRepository.lock(11)
            heldStripes.hold(11)
            objectLockRepository.lock(63)
            heldStripes.hold(63)
        }
    }

    @Test
    fun `withLock - Records nothing, and takes nothing, when it was named no key`() = runTest {
        coEvery { heldStripes.held() } returns emptyList()

        manager.withLock { }

        coVerify(exactly = 0) { heldStripes.hold(any()) }
        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    @Test
    fun `withLock - Runs the block once and answers what it answered`() = runTest {
        coEvery { heldStripes.held() } returns emptyList()
        coEvery { heldStripes.hold(any()) } just runs
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
    fun `withLock - Runs the block without taking anything the transaction already holds`() = runTest {
        coEvery { heldStripes.held() } returns listOf(11, 13)
        var ran = false

        manager.withLock(identifier) { ran = true }

        assertTrue(ran)
        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    @Test
    fun `withLock - Refuses a lock the transaction does not already hold`() = runTest {
        coEvery { heldStripes.held() } returns listOf(11)
        var ran = false

        val thrown = assertThrows<BusinessException> {
            manager.withLock(otherUser) { ran = true }
        }

        assertEquals("lock.second", thrown.detailsId)
        assertFalse(ran)
        coVerify(exactly = 0) { objectLockRepository.lock(63) }
    }
}

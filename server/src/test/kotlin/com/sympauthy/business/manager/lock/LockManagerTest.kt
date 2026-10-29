package com.sympauthy.business.manager.lock

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.data.repository.ObjectLockRepository
import com.sympauthy.monitoring.telemetry.LockMetrics
import com.sympauthy.monitoring.telemetry.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import io.r2dbc.spi.R2dbcTimeoutException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
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

    @SpyK
    var lockMetrics = LockMetrics(OpenTelemetry.noop())

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

        manager.withLock(listOf(otherUser, identifier, user)) { }

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

        manager.withLock(listOf(identifier, LockKey.IdentifierValue("someone@example.com"))) { }

        coVerify(exactly = 1) { objectLockRepository.lock(13) }
    }

    @Test
    fun `withLock - Records each stripe against the transaction as it takes it`() = runTest {
        coEvery { heldStripes.held() } returns emptyList()
        coEvery { heldStripes.hold(any()) } just runs
        coEvery { objectLockRepository.lock(any()) } returns 0

        manager.withLock(listOf(otherUser, user)) { }

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

        manager.withLock(emptyList()) { }

        coVerify(exactly = 0) { heldStripes.hold(any()) }
        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    @Test
    fun `withLock - Runs the block once and answers what it answered`() = runTest {
        coEvery { heldStripes.held() } returns emptyList()
        coEvery { heldStripes.hold(any()) } just runs
        coEvery { objectLockRepository.lock(any()) } returns 0
        var runs = 0

        val answer = manager.withLock(listOf(user)) {
            runs++
            "answered"
        }

        assertEquals("answered", answer)
        assertEquals(1, runs)
    }

    @Test
    fun `withLock - Records the wait for the stripes it took as a success`() = runTest {
        coEvery { heldStripes.held() } returns emptyList()
        coEvery { heldStripes.hold(any()) } just runs
        coEvery { objectLockRepository.lock(any()) } returns 0

        manager.withLock(listOf(user, identifier)) { }

        verify { lockMetrics.recordWait("identifier_value+user", any(), Outcome.Success) }
    }

    @Test
    fun `withLock - Records a wait that failed under the SQLSTATE the database answered with`() = runTest {
        val timeout = R2dbcTimeoutException("canceling statement due to lock timeout", "55P03")
        coEvery { heldStripes.held() } returns emptyList()
        coEvery { objectLockRepository.lock(11) } throws timeout
        var ran = false

        val thrown = assertThrows<R2dbcTimeoutException> { manager.withLock(listOf(user)) { ran = true } }

        assertSame(timeout, thrown)
        assertFalse(ran)
        verify { lockMetrics.recordWait("user", any(), Outcome.Error("55P03")) }
    }

    @Test
    fun `withLock - Runs the block without taking anything the transaction already holds`() = runTest {
        coEvery { heldStripes.held() } returns listOf(11, 13)
        var ran = false

        manager.withLock(listOf(identifier)) { ran = true }

        assertTrue(ran)
        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    @Test
    fun `withLock - Refuses a lock the transaction does not already hold`() = runTest {
        coEvery { heldStripes.held() } returns listOf(11)
        var ran = false

        val thrown = assertThrows<BusinessException> {
            manager.withLock(listOf(otherUser)) { ran = true }
        }

        assertEquals("lock.second", thrown.detailsId)
        assertFalse(ran)
        coVerify(exactly = 0) { objectLockRepository.lock(63) }
    }
}

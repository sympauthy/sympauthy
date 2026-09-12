package com.sympauthy.business.manager.lock

import com.sympauthy.business.manager.lock.ScheduledJob.CLEAN_ABANDONED_ACCOUNTS
import com.sympauthy.data.repository.JobLeaseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class JobLeaseManagerTest {

    @MockK
    lateinit var jobLeaseRepository: JobLeaseRepository

    @InjectMockKs
    lateinit var manager: JobLeaseManager

    /** What the database answers when asked the time, which is the only clock this manager reads. */
    private val databaseNow: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

    @BeforeEach
    fun stubDatabaseClock() {
        coEvery { jobLeaseRepository.now() } returns databaseNow
    }

    @Test
    fun `withLease - Runs the block and releases the lease it took`() = runTest {
        coEvery { jobLeaseRepository.acquire(any(), any(), any(), any()) } returns 1
        coEvery { jobLeaseRepository.release(any(), any()) } returns 1
        var ran = false

        val held = manager.withLease(CLEAN_ABANDONED_ACCOUNTS) { ran = true }

        assertTrue(held)
        assertTrue(ran)
        coVerify(exactly = 1) {
            jobLeaseRepository.release("clean_abandoned_accounts", any())
        }
    }

    @Test
    fun `withLease - Times the lease by the database's clock and not by this instance's`() = runTest {
        coEvery { jobLeaseRepository.release(any(), any()) } returns 1
        coEvery {
            jobLeaseRepository.acquire(
                name = "clean_abandoned_accounts",
                holder = any(),
                now = databaseNow,
                expiresAt = databaseNow.plus(CLEAN_ABANDONED_ACCOUNTS.leaseDuration)
            )
        } returns 1

        assertTrue(manager.withLease(CLEAN_ABANDONED_ACCOUNTS) { })
    }

    @Test
    fun `withLease - Does not run the block when another instance holds the lease`() = runTest {
        coEvery { jobLeaseRepository.acquire(any(), any(), any(), any()) } returns 0
        var ran = false

        val held = manager.withLease(CLEAN_ABANDONED_ACCOUNTS) { ran = true }

        assertFalse(held)
        assertFalse(ran)
        coVerify(exactly = 0) { jobLeaseRepository.release(any(), any()) }
    }

    @Test
    fun `withLease - Releases the lease when the block fails, and lets the failure through`() = runTest {
        coEvery { jobLeaseRepository.acquire(any(), any(), any(), any()) } returns 1
        coEvery { jobLeaseRepository.release(any(), any()) } returns 1

        assertThrows<IllegalStateException> {
            manager.withLease(CLEAN_ABANDONED_ACCOUNTS) { error("the run failed") }
        }

        coVerify(exactly = 1) {
            jobLeaseRepository.release("clean_abandoned_accounts", any())
        }
    }

    @Test
    fun `withLease - Releases under the holder that took it`() = runTest {
        val takenBy = mutableListOf<String>()
        val releasedBy = mutableListOf<String>()
        coEvery {
            jobLeaseRepository.acquire(any(), capture(takenBy), any(), any())
        } returns 1
        coEvery { jobLeaseRepository.release(any(), capture(releasedBy)) } returns 1

        manager.withLease(CLEAN_ABANDONED_ACCOUNTS) { }

        assertTrue(takenBy.single() == releasedBy.single())
    }
}

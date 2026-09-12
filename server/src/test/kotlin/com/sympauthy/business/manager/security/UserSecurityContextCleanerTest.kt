package com.sympauthy.business.manager.security

import com.sympauthy.config.model.DisabledAdvancedConfig
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.SecurityContextGeoConfig
import com.sympauthy.config.model.SecurityContextIpConfig
import com.sympauthy.config.model.advancedConfigOf
import com.sympauthy.config.model.noNamedGeoHeaders
import com.sympauthy.data.model.UserSecurityContextEntity
import com.sympauthy.data.repository.UserSecurityContextRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * The sweep, and the two things about it that are decisions rather than mechanics: that the cutoff comes
 * from the clock at the moment it runs, and that one run takes bounded batches rather than one statement.
 */
@ExtendWith(MockKExtension::class)
class UserSecurityContextCleanerTest {

    private val contextRepository = mockk<UserSecurityContextRepository>(relaxed = true)

    @Test
    fun `clean - Deletes what was last seen before the retention`() = runTest {
        val cleaner = cleanerOf(Duration.ofDays(180))
        val batch = rows(1)
        coEvery { contextRepository.claimExpired(any(), any()) } returnsMany listOf(batch, emptyList())
        coEvery { contextRepository.deleteByIdInAndLastSeenDateLessThan(any(), any()) } returns 1

        assertEquals(1, cleaner.clean())
    }

    /**
     * The cutoff is the retention subtracted from now rather than a column stamped when the row was
     * written, which is what makes lowering the retention take effect on the next run.
     */
    @Test
    fun `clean - Takes the cutoff from the retention and the clock`() = runTest {
        val cleaner = cleanerOf(Duration.ofDays(30))
        val cutoff = slot<LocalDateTime>()
        coEvery { contextRepository.claimExpired(capture(cutoff), any()) } returns emptyList()

        cleaner.clean()

        val expected = LocalDateTime.now().minusDays(30)
        assertTrue(
            Duration.between(cutoff.captured, expected).abs() < Duration.ofMinutes(1),
            "The cutoff was ${cutoff.captured}, which is not thirty days back from now."
        )
    }

    /**
     * A retention is the only thing that says what expired means, so guessing one would delete somebody's
     * history on a number no operator chose.
     */
    @Test
    fun `clean - Touches nothing where the configuration did not parse`() = runTest {
        val cleaner = UserSecurityContextCleaner(contextRepository, DisabledAdvancedConfig(emptyList()))

        assertEquals(0, cleaner.clean())

        coVerify(exactly = 0) { contextRepository.claimExpired(any(), any()) }
    }

    @Test
    fun `clean - Comes back for another batch while a run took everything it asked for`() = runTest {
        val cleaner = cleanerOf(Duration.ofDays(180))
        val full = slot<Int>()
        coEvery { contextRepository.claimExpired(any(), capture(full)) } returnsMany
            listOf(rows(BATCH_SIZE), rows(2), emptyList())
        coEvery { contextRepository.deleteByIdInAndLastSeenDateLessThan(any(), any()) } returnsMany
            listOf(BATCH_SIZE, 2)

        assertEquals(BATCH_SIZE + 2, cleaner.clean())

        coVerify(exactly = 2) { contextRepository.claimExpired(any(), any()) }
    }

    @Test
    fun `clean - Stops where a batch came back short of what it asked for`() = runTest {
        val cleaner = cleanerOf(Duration.ofDays(180))
        coEvery { contextRepository.claimExpired(any(), any()) } returns rows(3)
        coEvery { contextRepository.deleteByIdInAndLastSeenDateLessThan(any(), any()) } returns 3

        cleaner.clean()

        coVerify(exactly = 1) { contextRepository.claimExpired(any(), any()) }
    }

    /**
     * A place somebody signed in from again between the claim and the delete survives it, so a run may
     * remove fewer rows than it took — which is not the end of the table and not a reason to stop.
     */
    @Test
    fun `clean - Counts what it removed rather than what it claimed`() = runTest {
        val cleaner = cleanerOf(Duration.ofDays(180))
        coEvery { contextRepository.claimExpired(any(), any()) } returns rows(5)
        coEvery { contextRepository.deleteByIdInAndLastSeenDateLessThan(any(), any()) } returns 4

        assertEquals(4, cleaner.clean())
    }

    private fun cleanerOf(retention: Duration) = UserSecurityContextCleaner(
        contextRepository,
        advancedConfigOf(
            securityContext = SecurityContextConfig(
                ip = SecurityContextIpConfig(provider = null, header = null),
                geo = SecurityContextGeoConfig(false, emptyList(), noNamedGeoHeaders()),
                knownUserRetention = retention
            )
        )
    )

    private fun rows(count: Int) = List(count) {
        UserSecurityContextEntity(
            userId = UUID.randomUUID(),
            fingerprint = "e".repeat(64),
            ip = "203.0.113.7",
            userAgent = null,
            firstSeenDate = LocalDateTime.now().minusDays(300),
            lastSeenDate = LocalDateTime.now().minusDays(300)
        ).also { it.id = UUID.randomUUID() }
    }

    private companion object {

        /** What the cleaner asks for, named here because a full batch is what makes it come back. */
        const val BATCH_SIZE = 1_000
    }
}

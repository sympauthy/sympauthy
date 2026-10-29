package com.sympauthy.business.manager.auth

import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.AuthorizationCodeRepository
import com.sympauthy.data.repository.GrantRepository
import com.sympauthy.monitoring.telemetry.InMemoryMetrics
import com.sympauthy.monitoring.telemetry.JobMetrics
import com.sympauthy.monitoring.telemetry.JobMetrics.Companion.JOB_DELETIONS
import com.sympauthy.monitoring.telemetry.attributes
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class GrantCleanerTest {

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var authorizationCodeRepository: AuthorizationCodeRepository

    @MockK
    lateinit var grantRepository: GrantRepository

    private val metrics = InMemoryMetrics()

    @SpyK
    var jobMetrics = JobMetrics(metrics.openTelemetry)

    @InjectMockKs
    lateinit var grantCleaner: GrantCleaner

    @Test
    fun `clean - Deletes the tokens and the codes before the grants they leave finished`() = runTest {
        val cutoff = slot<LocalDateTime>()
        val codeCutoff = slot<LocalDateTime>()
        val latestCutoff = LocalDateTime.now().minus(GrantCleaner.EXPIRATION_GRACE)
        coEvery { tokenRepository.deleteByExpirationDateLessThan(capture(cutoff)) } returns 3
        coEvery { tokenRepository.deleteUnderRevokedGrants() } returns 2
        coEvery { authorizationCodeRepository.deleteByExpirationDateLessThan(capture(codeCutoff)) } returns 6
        coEvery { grantRepository.deleteFinished() } returns 4

        val result = grantCleaner.clean()

        assertEquals(5, result.tokenCount)
        assertEquals(6, result.authorizationCodeCount)
        assertEquals(4, result.grantCount)
        assertEquals(5, metrics.value(JOB_DELETIONS, attributes("job" to "clean_finished_grants", "kind" to "token")))
        assertFalse(cutoff.captured.isBefore(latestCutoff), "the cutoff should be a grace before now")
        assertTrue(
            cutoff.captured.isBefore(LocalDateTime.now().minus(GrantCleaner.EXPIRATION_GRACE).plusSeconds(1)),
            "a token should be kept the grace past its expiry"
        )
        assertEquals(cutoff.captured, codeCutoff.captured, "a code should be kept the same grace as a token")
        coVerifyOrder {
            tokenRepository.deleteByExpirationDateLessThan(any())
            tokenRepository.deleteUnderRevokedGrants()
            authorizationCodeRepository.deleteByExpirationDateLessThan(any())
            grantRepository.deleteFinished()
        }
    }
}

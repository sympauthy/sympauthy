package com.sympauthy.business.manager.auth

import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.GrantRepository
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
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
    lateinit var grantRepository: GrantRepository

    @InjectMockKs
    lateinit var grantCleaner: GrantCleaner

    @Test
    fun `clean - Deletes the tokens before the grants they leave finished`() = runTest {
        val cutoff = slot<LocalDateTime>()
        val latestCutoff = LocalDateTime.now().minus(GrantCleaner.EXPIRED_TOKEN_GRACE)
        coEvery { tokenRepository.deleteByExpirationDateLessThan(capture(cutoff)) } returns 3
        coEvery { tokenRepository.deleteUnderRevokedGrants() } returns 2
        coEvery { grantRepository.deleteFinished() } returns 4

        val result = grantCleaner.clean()

        assertEquals(5, result.tokenCount)
        assertEquals(4, result.grantCount)
        assertFalse(cutoff.captured.isBefore(latestCutoff), "the cutoff should be a grace before now")
        assertTrue(
            cutoff.captured.isBefore(LocalDateTime.now().minus(GrantCleaner.EXPIRED_TOKEN_GRACE).plusSeconds(1)),
            "a token should be kept the grace past its expiry"
        )
        coVerifyOrder {
            tokenRepository.deleteByExpirationDateLessThan(any())
            tokenRepository.deleteUnderRevokedGrants()
            grantRepository.deleteFinished()
        }
    }
}

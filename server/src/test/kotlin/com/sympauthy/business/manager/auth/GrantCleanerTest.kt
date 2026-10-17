package com.sympauthy.business.manager.auth

import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.GrantRepository
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

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
        coEvery { tokenRepository.deleteByExpirationDateLessThan(any()) } returns 3
        coEvery { tokenRepository.deleteUnderRevokedGrants() } returns 2
        coEvery { grantRepository.deleteFinished() } returns 4

        val result = grantCleaner.clean()

        assertEquals(5, result.tokenCount)
        assertEquals(4, result.grantCount)
        coVerifyOrder {
            tokenRepository.deleteByExpirationDateLessThan(any())
            tokenRepository.deleteUnderRevokedGrants()
            grantRepository.deleteFinished()
        }
    }
}

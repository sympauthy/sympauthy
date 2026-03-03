package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID

@Suppress("unused")
@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowTotpChallengeManagerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowTotpChallengeManager

    private val userId = UUID.randomUUID()
    private val user = mockk<User> { every { id } returns userId }
    private val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()

    // --- validateTotpChallenge ---

    @Test
    fun `validateTotpChallenge - Returns attempt unchanged when code is valid`() = runTest {
        coEvery { totpManager.isCodeValidForUser(userId, "123456") } returns true

        val result = manager.validateTotpChallenge(authorizeAttempt, user, "123456")

        assertSame(authorizeAttempt, result)
    }

    @Test
    fun `validateTotpChallenge - Throws recoverable exception when code is null`() = runTest {
        val exception = assertThrows<BusinessException> {
            manager.validateTotpChallenge(authorizeAttempt, user, null)
        }

        assertEquals("flow.mfa.totp.challenge.invalid_code", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { totpManager.isCodeValidForUser(any(), any()) }
    }

    @Test
    fun `validateTotpChallenge - Throws recoverable exception when code is blank`() = runTest {
        val exception = assertThrows<BusinessException> {
            manager.validateTotpChallenge(authorizeAttempt, user, "")
        }

        assertEquals("flow.mfa.totp.challenge.invalid_code", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { totpManager.isCodeValidForUser(any(), any()) }
    }

    @Test
    fun `validateTotpChallenge - Throws recoverable exception when code does not match any enrollment`() = runTest {
        coEvery { totpManager.isCodeValidForUser(userId, "000000") } returns false

        val exception = assertThrows<BusinessException> {
            manager.validateTotpChallenge(authorizeAttempt, user, "000000")
        }

        assertEquals("flow.mfa.totp.challenge.invalid_code", exception.detailsId)
        assertTrue(exception.recoverable)
    }
}

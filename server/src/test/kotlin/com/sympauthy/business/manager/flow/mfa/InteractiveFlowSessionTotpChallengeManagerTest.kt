package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
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
import java.util.*

@Suppress("unused")
@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class InteractiveFlowSessionTotpChallengeManagerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionTotpChallengeManager

    private val userId = UUID.randomUUID()
    private val user = mockk<User>()
    private val session = mockk<OnGoingInteractiveFlowSession>()

    /** Identifies the user, for the paths that get as far as checking a code against them. */
    private fun identifyUser() {
        every { user.id } returns userId
    }

    // --- validateTotpChallenge ---

    @Test
    fun `validateTotpChallenge - Records mfaPassedDate and returns updated session when code is valid`() = runTest {
        val updatedSession = mockk<OnGoingInteractiveFlowSession>()
        identifyUser()
        coEvery { totpManager.isCodeValidForUser(userId, "123456") } returns true
        coEvery { sessionManager.setMfaPassed(session) } returns updatedSession

        val result = manager.validateTotpChallenge(session, user, "123456")

        assertSame(updatedSession, result)
    }

    @Test
    fun `validateTotpChallenge - Throws recoverable exception when code is null`() = runTest {
        val exception = assertThrows<BusinessException> {
            manager.validateTotpChallenge(session, user, null)
        }

        assertEquals("flow.mfa.totp.challenge.invalid_code", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { totpManager.isCodeValidForUser(any(), any()) }
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    @Test
    fun `validateTotpChallenge - Throws recoverable exception when code is blank`() = runTest {
        val exception = assertThrows<BusinessException> {
            manager.validateTotpChallenge(session, user, "")
        }

        assertEquals("flow.mfa.totp.challenge.invalid_code", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { totpManager.isCodeValidForUser(any(), any()) }
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    @Test
    fun `validateTotpChallenge - Throws recoverable exception when code does not match any enrollment`() = runTest {
        identifyUser()
        coEvery { totpManager.isCodeValidForUser(userId, "000000") } returns false

        val exception = assertThrows<BusinessException> {
            manager.validateTotpChallenge(session, user, "000000")
        }

        assertEquals("flow.mfa.totp.challenge.invalid_code", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }
}

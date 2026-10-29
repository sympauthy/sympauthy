package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.user.User
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics.Companion.AUTHENTICATION_ATTEMPTS
import com.sympauthy.monitoring.telemetry.InMemoryMetrics
import com.sympauthy.monitoring.telemetry.attributes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@Suppress("unused")
@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionTotpChallengeManagerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    private val metrics = InMemoryMetrics()

    @SpyK
    var authenticationMetrics = AuthenticationMetrics(metrics.openTelemetry)

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionTotpChallengeManager

    private val user = mockk<User>()
    private val session = mockk<OnGoingInteractiveFlowSession>()

    @Test
    fun `validateTotpChallenge - Records mfaPassedDate and returns updated session when code is valid`() = runTest {
        val updatedSession = mockk<OnGoingInteractiveFlowSession>()
        coEvery { totpManager.isCodeValidForUser(user, "123456") } returns true
        coEvery { sessionManager.setMfaPassed(session) } returns updatedSession

        val result = manager.validateTotpChallenge(session, user, "123456")

        assertSame(updatedSession, result)
    }

    @Test
    fun `validateTotpChallenge - Records a refused code as a refused second factor`() = runTest {
        coEvery { totpManager.isCodeValidForUser(user, "000000") } returns false

        assertThrows<BusinessException> { manager.validateTotpChallenge(session, user, "000000") }

        assertEquals(
            1,
            metrics.value(
                AUTHENTICATION_ATTEMPTS,
                attributes(
                    "method" to "totp",
                    "kind" to "second_factor",
                    "outcome" to "refused",
                    "error.type" to "flow.mfa.totp.challenge.invalid_code"
                )
            )
        )
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
        coEvery { totpManager.isCodeValidForUser(user, "000000") } returns false

        val exception = assertThrows<BusinessException> {
            manager.validateTotpChallenge(session, user, "000000")
        }

        assertEquals("flow.mfa.totp.challenge.invalid_code", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }
}

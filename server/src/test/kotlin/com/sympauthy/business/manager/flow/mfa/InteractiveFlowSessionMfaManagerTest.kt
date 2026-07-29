package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class InteractiveFlowSessionMfaManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var totpManager: TotpManager

    private val userId = UUID.randomUUID()
    private val user = mockk<User> { every { id } returns userId }
    private val session = mockk<OnGoingInteractiveFlowSession>()

    private fun managerWith(mfaConfig: MfaConfig) = InteractiveFlowSessionMfaManager(
        uncheckedMfaConfig = mfaConfig,
        sessionManager = sessionManager,
        totpManager = totpManager
    )

    // --- getMfaResult ---

    @Test
    fun `getMfaResult - required and not enrolled - auto-redirects to TOTP enrollment`() = runTest {
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()

        val result = manager.getMfaResult(user)

        assertEquals(MfaAutoRedirect(InteractiveFlowStep.MfaTotpEnroll), result)
    }

    @Test
    fun `getMfaResult - required and enrolled - auto-redirects to TOTP challenge`() = runTest {
        val enrollment = mockk<TotpEnrollment>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)

        val result = manager.getMfaResult(user)

        assertEquals(MfaAutoRedirect(InteractiveFlowStep.MfaTotpChallenge), result)
    }

    @Test
    fun `getMfaResult - optional and not enrolled - returns method selection with TOTP enrollment and skip`() =
        runTest {
            val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

            coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()

            val result = manager.getMfaResult(user)

            assertEquals(
                MfaMethodSelection(
                    methods = listOf(AvailableMfaMethod(name = "TOTP", step = InteractiveFlowStep.MfaTotpEnroll)),
                    skippable = true
                ),
                result
            )
        }

    @Test
    fun `getMfaResult - optional and enrolled - auto-redirects to TOTP challenge`() = runTest {
        val enrollment = mockk<TotpEnrollment>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)

        val result = manager.getMfaResult(user)

        assertEquals(MfaAutoRedirect(InteractiveFlowStep.MfaTotpChallenge), result)
    }

    // --- selectRequiredMfaPurpose ---

    @Test
    fun `selectRequiredMfaPurpose - Returns null when MFA is disabled`() = runTest {
        val manager = managerWith(EnabledMfaConfig(totp = false, required = false))

        assertNull(manager.selectRequiredMfaPurpose(session))
    }

    @Test
    fun `selectRequiredMfaPurpose - Returns MFA_CHALLENGE when the user is enrolled`() = runTest {
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))
        every { session.userId } returns userId
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(mockk())

        assertEquals(InteractiveFlowPurpose.MFA_CHALLENGE, manager.selectRequiredMfaPurpose(session))
    }

    @Test
    fun `selectRequiredMfaPurpose - Returns MFA_ENROLLMENT on sign-up when not enrolled`() = runTest {
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))
        every { session.userId } returns userId
        every { session.signedUp } returns true
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()

        assertEquals(InteractiveFlowPurpose.MFA_ENROLLMENT, manager.selectRequiredMfaPurpose(session))
    }

    @Test
    fun `selectRequiredMfaPurpose - Returns MFA_ENROLLMENT on sign-in when required and not enrolled`() = runTest {
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))
        every { session.userId } returns userId
        every { session.signedUp } returns false
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()

        assertEquals(InteractiveFlowPurpose.MFA_ENROLLMENT, manager.selectRequiredMfaPurpose(session))
    }

    @Test
    fun `selectRequiredMfaPurpose - Returns null on sign-in when not enrolled and not required`() = runTest {
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))
        every { session.userId } returns userId
        every { session.signedUp } returns false
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()

        assertNull(manager.selectRequiredMfaPurpose(session))
    }

    // --- skipMfa ---

    @Test
    fun `skipMfa - Sets mfaPassed and returns updated session when MFA is optional and not enrolled`() = runTest {
        val updatedSession = mockk<OnGoingInteractiveFlowSession>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

        every { session.userId } returns userId
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()
        coEvery { sessionManager.setMfaPassed(session) } returns updatedSession

        val result = manager.skipMfa(session)

        assertSame(updatedSession, result)
        coVerify(exactly = 1) { sessionManager.setMfaPassed(session) }
    }

    @Test
    fun `skipMfa - Throws unrecoverable exception when MFA is required`() = runTest {
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))

        val exception = assertThrows<BusinessException> {
            manager.skipMfa(session)
        }

        assertEquals("flow.mfa.skip.not_allowed", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    @Test
    fun `skipMfa - Throws unrecoverable exception when MFA is optional but user is enrolled`() = runTest {
        val enrollment = mockk<TotpEnrollment>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

        every { session.userId } returns userId
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)

        val exception = assertThrows<BusinessException> {
            manager.skipMfa(session)
        }

        assertEquals("flow.mfa.skip.not_allowed_when_enrolled", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }
}

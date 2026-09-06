package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.confirm.InteractiveFlowSessionConfirmManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionMfaEnrollmentManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var confirmManager: InteractiveFlowSessionConfirmManager

    @MockK
    lateinit var userManager: UserManager

    private val userId = UUID.randomUUID()
    private val clientId = "client-x"
    private val session = mockk<OnGoingInteractiveFlowSession>()

    private fun managerWith(mfaConfig: MfaConfig) = InteractiveFlowSessionMfaEnrollmentManager(
        sessionManager = sessionManager,
        uncheckedMfaConfig = mfaConfig,
        totpManager = totpManager,
        confirmManager = confirmManager,
        userManager = userManager
    )

    private val optionalMfa = EnabledMfaConfig(totp = true, required = false)
    private val requiredMfa = EnabledMfaConfig(totp = true, required = true)

    @Test
    @Suppress("MaxLineLength")
    fun `startMfaEnrollmentSession - Creates a PLAIN session with the return and cancel URIs, and sets the user`() = runTest {
        val manager = managerWith(optionalMfa)
        coJustRun { userManager.checkPromoted(userId) }
        val returnUri = URI.create("https://client.example.com/enrolled")
        val cancelUri = URI.create("https://client.example.com/cancelled")
        val flow = mockk<AuthorizationFlow>()
        val newSession = mockk<OnGoingInteractiveFlowSession>()
        val withUser = mockk<OnGoingInteractiveFlowSession>()
        coEvery {
            sessionManager.newSession(
                purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
                initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
                flow = flow,
                successRedirectUri = returnUri,
                redirectType = InteractiveFlowRedirectType.PLAIN,
                cancelRedirectUri = cancelUri,
            )
        } returns newSession
        coEvery { sessionManager.setAuthenticatedUserId(newSession, userId) } returns withUser
        coEvery {
            confirmManager.setConfirm(withUser, ConfirmActionType.ENROLL_MFA, clientId)
        } returns mockk()

        val result = manager.startMfaEnrollmentSession(userId, returnUri, flow, clientId, cancelUri)

        assertSame(withUser, result)
        coVerify { confirmManager.setConfirm(withUser, ConfirmActionType.ENROLL_MFA, clientId) }
    }

    @Test
    fun `startMfaEnrollmentSession - Passes a null cancel URI through when none is provided`() = runTest {
        val manager = managerWith(optionalMfa)
        coJustRun { userManager.checkPromoted(userId) }
        val returnUri = URI.create("https://client.example.com/enrolled")
        val flow = mockk<AuthorizationFlow>()
        val newSession = mockk<OnGoingInteractiveFlowSession>()
        val withUser = mockk<OnGoingInteractiveFlowSession>()
        coEvery {
            sessionManager.newSession(
                purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
                initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
                flow = flow,
                successRedirectUri = returnUri,
                redirectType = InteractiveFlowRedirectType.PLAIN,
                cancelRedirectUri = null,
            )
        } returns newSession
        coEvery { sessionManager.setAuthenticatedUserId(newSession, userId) } returns withUser
        coEvery {
            confirmManager.setConfirm(withUser, ConfirmActionType.ENROLL_MFA, clientId)
        } returns mockk()

        val result = manager.startMfaEnrollmentSession(userId, returnUri, flow, clientId)

        assertSame(withUser, result)
    }

    @Test
    fun `startMfaEnrollmentSession - Refuses an account a sign-up has not finished`() = runTest {
        val manager = managerWith(optionalMfa)
        val refusal = mockk<BusinessException>()
        coEvery { userManager.checkPromoted(userId) } throws refusal

        assertSame(
            refusal,
            assertThrows<BusinessException> {
                manager.startMfaEnrollmentSession(
                    userId,
                    URI.create("https://client.example.com/enrolled"),
                    mockk(),
                    clientId,
                )
            },
        )

        coVerify(exactly = 0) { sessionManager.newSession(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `startMfaEnrollmentSession - Stores a null client id on the confirm record for an admin-initiated enrollment`() =
        runTest {
            val manager = managerWith(optionalMfa)
            coJustRun { userManager.checkPromoted(userId) }
            val returnUri = URI.create("https://client.example.com/enrolled")
            val flow = mockk<AuthorizationFlow>()
            val newSession = mockk<OnGoingInteractiveFlowSession>()
            val withUser = mockk<OnGoingInteractiveFlowSession>()
            coEvery {
                sessionManager.newSession(
                    purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
                    initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
                    flow = flow,
                    successRedirectUri = returnUri,
                    redirectType = InteractiveFlowRedirectType.PLAIN,
                    cancelRedirectUri = null,
                )
            } returns newSession
            coEvery { sessionManager.setAuthenticatedUserId(newSession, userId) } returns withUser
            // Admin-initiated: the confirm record must carry a null client id (rendered as "an administrator").
            coEvery {
                confirmManager.setConfirm(withUser, ConfirmActionType.ENROLL_MFA, null)
            } returns mockk()

            val result = manager.startMfaEnrollmentSession(userId, returnUri, flow, initiatingClientId = null)

            assertSame(withUser, result)
        }

    @Test
    fun `getEnrollmentRoutingResult - optional and not standalone - offers TOTP enrollment with skip`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE

        val result = manager.getEnrollmentRoutingResult(session)

        assertEquals(
            MfaMethodSelection(
                methods = listOf(AvailableMfaMethod(name = "TOTP", step = InteractiveFlowStep.MfaTotpEnroll)),
                skippable = true
            ),
            result
        )
    }

    @Test
    fun `getEnrollmentRoutingResult - required - auto-redirects to TOTP enrollment`() = runTest {
        val manager = managerWith(requiredMfa)

        val result = manager.getEnrollmentRoutingResult(session)

        assertEquals(MfaAutoRedirect(InteractiveFlowStep.MfaTotpEnroll), result)
    }

    @Test
    fun `getEnrollmentRoutingResult - standalone - auto-redirects to TOTP enrollment`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT

        val result = manager.getEnrollmentRoutingResult(session)

        assertEquals(MfaAutoRedirect(InteractiveFlowStep.MfaTotpEnroll), result)
    }

    @Test
    fun `isEnrollmentSkippable - false when MFA is required`() = runTest {
        val manager = managerWith(requiredMfa)

        assertFalse(manager.isEnrollmentSkippable(session))
    }

    @Test
    fun `isEnrollmentSkippable - false for a standalone enrollment`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT

        assertFalse(manager.isEnrollmentSkippable(session))
    }

    @Test
    fun `isEnrollmentSkippable - true when optional and not standalone`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE

        assertTrue(manager.isEnrollmentSkippable(session))
    }

    @Test
    fun `skipMfa - Sets mfaPassed and returns updated session when optional and not enrolled`() = runTest {
        val manager = managerWith(optionalMfa)
        val updatedSession = mockk<OnGoingInteractiveFlowSession>()

        every { session.initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        every { session.userId } returns userId
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()
        coEvery { sessionManager.setMfaPassed(session) } returns updatedSession

        assertSame(updatedSession, manager.skipMfa(session))
    }

    @Test
    fun `skipMfa - Throws when MFA is required`() = runTest {
        val manager = managerWith(requiredMfa)

        val exception = assertThrows<BusinessException> { manager.skipMfa(session) }

        assertEquals("flow.mfa.skip.not_allowed", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    @Test
    fun `skipMfa - Throws for a standalone enrollment`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT

        val exception = assertThrows<BusinessException> { manager.skipMfa(session) }

        assertEquals("flow.mfa.skip.not_allowed_standalone", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    @Test
    fun `skipMfa - Throws when optional but user is enrolled`() = runTest {
        val manager = managerWith(optionalMfa)

        every { session.initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        every { session.userId } returns userId
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(mockk())

        val exception = assertThrows<BusinessException> { manager.skipMfa(session) }

        assertEquals("flow.mfa.skip.not_allowed_when_enrolled", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }
}

package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionRedirectUriBuilder
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
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
import java.net.URI
import java.util.*

@Suppress("unused")
@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionMfaManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var redirectUriBuilder: InteractiveAuthFlowSessionRedirectUriBuilder

    private val userId = UUID.randomUUID()
    private val user = mockk<User> { every { id } returns userId }
    private val session = mockk<OnGoingInteractiveFlowSession>()
    private val flow = mockk<InteractiveFlow>()
    private val skipEndpointPath = "/api/v1/flow/mfa/skip"

    private fun managerWith(mfaConfig: MfaConfig) = InteractiveFlowSessionMfaManager(
        uncheckedMfaConfig = mfaConfig,
        sessionManager = sessionManager,
        totpManager = totpManager,
        redirectUriBuilder = redirectUriBuilder
    )

    // --- getMfaResult ---

    @Test
    fun `getMfaResult - required and not enrolled - auto-redirects to TOTP enrollment`() = runTest {
        val enrollUri = URI("https://example.com/mfa/totp/enroll?state=abc")
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()
        coEvery { redirectUriBuilder.getMfaTotpEnrollUri(session, flow) } returns enrollUri

        val result = manager.getMfaResult(session, user, flow, skipEndpointPath)

        assertEquals(MfaAutoRedirect(enrollUri), result)
    }

    @Test
    fun `getMfaResult - required and enrolled - auto-redirects to TOTP challenge`() = runTest {
        val challengeUri = URI("https://example.com/mfa/totp/challenge?state=abc")
        val enrollment = mockk<TotpEnrollment>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)
        coEvery { redirectUriBuilder.getMfaTotpChallengeUri(session, flow) } returns challengeUri

        val result = manager.getMfaResult(session, user, flow, skipEndpointPath)

        assertEquals(MfaAutoRedirect(challengeUri), result)
    }

    @Test
    fun `getMfaResult - optional and not enrolled - returns method selection with TOTP enrollment and skip`() =
        runTest {
            val enrollUri = URI("https://example.com/mfa/totp/enroll?state=abc")
            val skipUri = URI("https://example.com/mfa/skip?state=abc")
            val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

            coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()
            coEvery { redirectUriBuilder.getMfaTotpEnrollUri(session, flow) } returns enrollUri
            coEvery { redirectUriBuilder.getMfaSkipUri(session, skipEndpointPath) } returns skipUri

            val result = manager.getMfaResult(session, user, flow, skipEndpointPath)

            assertEquals(
                MfaMethodSelection(
                    methods = listOf(AvailableMfaMethod(name = "TOTP", uri = enrollUri)),
                    skipUri = skipUri
                ),
                result
            )
        }

    @Test
    fun `getMfaResult - optional and enrolled - auto-redirects to TOTP challenge`() = runTest {
        val challengeUri = URI("https://example.com/mfa/totp/challenge?state=abc")
        val enrollment = mockk<TotpEnrollment>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)
        coEvery { redirectUriBuilder.getMfaTotpChallengeUri(session, flow) } returns challengeUri

        val result = manager.getMfaResult(session, user, flow, skipEndpointPath)

        assertEquals(MfaAutoRedirect(challengeUri), result)
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

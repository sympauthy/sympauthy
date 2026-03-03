package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
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
import java.util.UUID

@Suppress("unused")
@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowMfaManagerTest {

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder

    private val userId = UUID.randomUUID()
    private val user = mockk<User> { every { id } returns userId }
    private val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
    private val flow = mockk<WebAuthorizationFlow>()
    private val skipEndpointPath = "/api/v1/flow/mfa/skip"

    private fun managerWith(mfaConfig: MfaConfig) = WebAuthorizationFlowMfaManager(
        uncheckedMfaConfig = mfaConfig,
        authorizeAttemptManager = authorizeAttemptManager,
        totpManager = totpManager,
        redirectUriBuilder = redirectUriBuilder
    )

    // --- getMfaResult ---

    @Test
    fun `getMfaResult - required and not enrolled - auto-redirects to TOTP enrollment`() = runTest {
        val enrollUri = URI("https://example.com/mfa/totp/enroll?state=abc")
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()
        coEvery { redirectUriBuilder.getMfaTotpEnrollUri(authorizeAttempt, flow) } returns enrollUri

        val result = manager.getMfaResult(authorizeAttempt, user, flow, skipEndpointPath)

        assertEquals(MfaAutoRedirect(enrollUri), result)
    }

    @Test
    fun `getMfaResult - required and enrolled - auto-redirects to TOTP challenge`() = runTest {
        val challengeUri = URI("https://example.com/mfa/totp/challenge?state=abc")
        val enrollment = mockk<TotpEnrollment>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)
        coEvery { redirectUriBuilder.getMfaTotpChallengeUri(authorizeAttempt, flow) } returns challengeUri

        val result = manager.getMfaResult(authorizeAttempt, user, flow, skipEndpointPath)

        assertEquals(MfaAutoRedirect(challengeUri), result)
    }

    @Test
    fun `getMfaResult - optional and not enrolled - returns method selection with TOTP enrollment and skip`() = runTest {
        val enrollUri = URI("https://example.com/mfa/totp/enroll?state=abc")
        val skipUri = URI("https://example.com/mfa/skip?state=abc")
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()
        coEvery { redirectUriBuilder.getMfaTotpEnrollUri(authorizeAttempt, flow) } returns enrollUri
        coEvery { redirectUriBuilder.getMfaSkipUri(authorizeAttempt, skipEndpointPath) } returns skipUri

        val result = manager.getMfaResult(authorizeAttempt, user, flow, skipEndpointPath)

        assertEquals(
            MfaMethodSelection(
                methods = listOf(AvailableMfaMethod(name = "TOTP", uri = enrollUri)),
                skipUri = skipUri
            ),
            result
        )
    }

    @Test
    fun `getMfaResult - optional and enrolled - returns method selection with TOTP challenge and skip`() = runTest {
        val challengeUri = URI("https://example.com/mfa/totp/challenge?state=abc")
        val skipUri = URI("https://example.com/mfa/skip?state=abc")
        val enrollment = mockk<TotpEnrollment>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)
        coEvery { redirectUriBuilder.getMfaTotpChallengeUri(authorizeAttempt, flow) } returns challengeUri
        coEvery { redirectUriBuilder.getMfaSkipUri(authorizeAttempt, skipEndpointPath) } returns skipUri

        val result = manager.getMfaResult(authorizeAttempt, user, flow, skipEndpointPath)

        assertEquals(
            MfaMethodSelection(
                methods = listOf(AvailableMfaMethod(name = "TOTP", uri = challengeUri)),
                skipUri = skipUri
            ),
            result
        )
    }

    // --- skipMfa ---

    @Test
    fun `skipMfa - Sets mfaPassed and returns updated attempt when MFA is optional`() = runTest {
        val updatedAttempt = mockk<OnGoingAuthorizeAttempt>()
        val manager = managerWith(EnabledMfaConfig(totp = true, required = false))

        coEvery { authorizeAttemptManager.setMfaPassed(authorizeAttempt) } returns updatedAttempt

        val result = manager.skipMfa(authorizeAttempt)

        assertSame(updatedAttempt, result)
        coVerify(exactly = 1) { authorizeAttemptManager.setMfaPassed(authorizeAttempt) }
    }

    @Test
    fun `skipMfa - Throws unrecoverable exception when MFA is required`() = runTest {
        val manager = managerWith(EnabledMfaConfig(totp = true, required = true))

        val exception = assertThrows<BusinessException> {
            manager.skipMfa(authorizeAttempt)
        }

        assertEquals("flow.mfa.skip.not_allowed", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { authorizeAttemptManager.setMfaPassed(any()) }
    }
}

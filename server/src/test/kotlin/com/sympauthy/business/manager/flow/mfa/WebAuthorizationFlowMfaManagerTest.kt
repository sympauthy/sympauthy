package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.User
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.UUID

@Suppress("unused")
@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowMfaManagerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder

    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowMfaManager

    private val userId = UUID.randomUUID()
    private val user = mockk<User> { every { id } returns userId }
    private val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
    private val flow = mockk<WebAuthorizationFlow>()

    // --- getMfaRedirectUri ---

    @Test
    fun `getMfaRedirectUri - Routes to challenge page when user has confirmed enrollment`() = runTest {
        val enrollment = mockk<TotpEnrollment>()
        val challengeUri = URI("https://example.com/mfa/totp/challenge?state=abc")

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment)
        coEvery { redirectUriBuilder.getMfaRedirectUri(authorizeAttempt, flow, true) } returns challengeUri

        val result = manager.getMfaRedirectUri(authorizeAttempt, user, flow)

        assertSame(challengeUri, result)
    }

    @Test
    fun `getMfaRedirectUri - Routes to enrollment page when user has no confirmed enrollment`() = runTest {
        val enrollUri = URI("https://example.com/mfa/totp/enroll?state=abc")

        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()
        coEvery { redirectUriBuilder.getMfaRedirectUri(authorizeAttempt, flow, false) } returns enrollUri

        val result = manager.getMfaRedirectUri(authorizeAttempt, user, flow)

        assertSame(enrollUri, result)
    }
}

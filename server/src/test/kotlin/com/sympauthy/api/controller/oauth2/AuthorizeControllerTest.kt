package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_RESPONSE_TYPE
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class AuthorizeControllerTest {

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    @InjectMockKs
    lateinit var controller: AuthorizeController

    // --- response_type validation ---

    @Test
    fun `authorize - Throws UNSUPPORTED_RESPONSE_TYPE when response_type is null`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            controller.authorize(
                responseType = null,
                uncheckedClientId = "client",
                uncheckedRedirectUri = "https://example.com/callback",
                uncheckedScopes = null,
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null,
                uncheckedInvitationToken = null
            )
        }
        assertEquals(UNSUPPORTED_RESPONSE_TYPE, exception.errorCode)
        assertEquals("authorize.response_type.missing", exception.detailsId)
    }

    @Test
    fun `authorize - Throws UNSUPPORTED_RESPONSE_TYPE when response_type is blank`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            controller.authorize(
                responseType = "   ",
                uncheckedClientId = "client",
                uncheckedRedirectUri = "https://example.com/callback",
                uncheckedScopes = null,
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null,
                uncheckedInvitationToken = null
            )
        }
        assertEquals(UNSUPPORTED_RESPONSE_TYPE, exception.errorCode)
        assertEquals("authorize.response_type.missing", exception.detailsId)
    }

    @Test
    fun `authorize - Throws UNSUPPORTED_RESPONSE_TYPE when response_type is token`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            controller.authorize(
                responseType = "token",
                uncheckedClientId = "client",
                uncheckedRedirectUri = "https://example.com/callback",
                uncheckedScopes = null,
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null,
                uncheckedInvitationToken = null
            )
        }
        assertEquals(UNSUPPORTED_RESPONSE_TYPE, exception.errorCode)
        assertEquals("authorize.response_type.invalid", exception.detailsId)
    }

    @Test
    fun `authorize - Throws UNSUPPORTED_RESPONSE_TYPE when response_type is unknown value`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            controller.authorize(
                responseType = "id_token",
                uncheckedClientId = "client",
                uncheckedRedirectUri = "https://example.com/callback",
                uncheckedScopes = null,
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null,
                uncheckedInvitationToken = null
            )
        }
        assertEquals(UNSUPPORTED_RESPONSE_TYPE, exception.errorCode)
        assertEquals("authorize.response_type.invalid", exception.detailsId)
    }

    @Test
    fun `authorize - Throws UNSUPPORTED_RESPONSE_TYPE when response_type has wrong casing`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            controller.authorize(
                responseType = "Code",
                uncheckedClientId = "client",
                uncheckedRedirectUri = "https://example.com/callback",
                uncheckedScopes = null,
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null,
                uncheckedInvitationToken = null
            )
        }
        assertEquals(UNSUPPORTED_RESPONSE_TYPE, exception.errorCode)
        assertEquals("authorize.response_type.invalid", exception.detailsId)
    }

    // --- Successful delegation ---

    @Test
    fun `authorize - Returns 303 redirect to sign-in URI on valid code request`() = runTest {
        val session = mockk<InteractiveFlowSession>()
        val flow = mockk<InteractiveFlow>()
        val signInUri = URI("https://auth.example.com/sign-in?state=abc")

        coEvery {
            interactiveAuthFlowSessionManager.startAuthorizationWith(
                uncheckedClientId = "client",
                uncheckedClientState = "my-state",
                uncheckedClientNonce = null,
                uncheckedScopes = "openid profile",
                uncheckedRedirectUri = "https://example.com/callback",
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null,
                uncheckedInvitationToken = null
            )
        } returns (session to flow)

        stubCurrentStep(session, flow, InteractiveFlowStep.SignIn, signInUri)

        val result = controller.authorize(
            responseType = "code",
            uncheckedClientId = "client",
            uncheckedRedirectUri = "https://example.com/callback",
            uncheckedScopes = "openid profile",
            uncheckedClientState = "my-state",
            uncheckedClientNonce = null,
            uncheckedCodeChallenge = null,
            uncheckedCodeChallengeMethod = null,
            uncheckedInvitationToken = null
        )

        assertEquals(HttpStatus.SEE_OTHER, result.status)
        assertEquals(signInUri, result.header("Location")?.let { URI(it) })
    }

    @Test
    fun `authorize - Passes all query parameters to startAuthorizationWith`() = runTest {
        val session = mockk<InteractiveFlowSession>()
        val flow = mockk<InteractiveFlow>()
        val signInUri = URI("https://auth.example.com/sign-in?state=abc")

        coEvery {
            interactiveAuthFlowSessionManager.startAuthorizationWith(
                uncheckedClientId = "my-client",
                uncheckedClientState = "my-state",
                uncheckedClientNonce = "my-nonce",
                uncheckedScopes = "openid email",
                uncheckedRedirectUri = "https://example.com/cb",
                uncheckedCodeChallenge = "challenge123",
                uncheckedCodeChallengeMethod = "S256"
            )
        } returns (session to flow)

        stubCurrentStep(session, flow, InteractiveFlowStep.SignIn, signInUri)

        controller.authorize(
            responseType = "code",
            uncheckedClientId = "my-client",
            uncheckedRedirectUri = "https://example.com/cb",
            uncheckedScopes = "openid email",
            uncheckedClientState = "my-state",
            uncheckedClientNonce = "my-nonce",
            uncheckedCodeChallenge = "challenge123",
            uncheckedCodeChallengeMethod = "S256",
            uncheckedInvitationToken = null
        )

        coVerify(exactly = 1) {
            interactiveAuthFlowSessionManager.startAuthorizationWith(
                uncheckedClientId = "my-client",
                uncheckedClientState = "my-state",
                uncheckedClientNonce = "my-nonce",
                uncheckedScopes = "openid email",
                uncheckedRedirectUri = "https://example.com/cb",
                uncheckedCodeChallenge = "challenge123",
                uncheckedCodeChallengeMethod = "S256"
            )
        }
    }

    @Test
    fun `authorize - Passes null for absent optional parameters`() = runTest {
        val session = mockk<InteractiveFlowSession>()
        val flow = mockk<InteractiveFlow>()
        val signInUri = URI("https://auth.example.com/sign-in?state=abc")

        coEvery {
            interactiveAuthFlowSessionManager.startAuthorizationWith(
                uncheckedClientId = "client",
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedScopes = null,
                uncheckedRedirectUri = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null,
                uncheckedInvitationToken = null
            )
        } returns (session to flow)

        stubCurrentStep(session, flow, InteractiveFlowStep.SignIn, signInUri)

        controller.authorize(
            responseType = "code",
            uncheckedClientId = "client",
            uncheckedRedirectUri = null,
            uncheckedScopes = null,
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedCodeChallenge = null,
            uncheckedCodeChallengeMethod = null,
            uncheckedInvitationToken = null
        )

        coVerify(exactly = 1) {
            interactiveAuthFlowSessionManager.startAuthorizationWith(
                uncheckedClientId = "client",
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedScopes = null,
                uncheckedRedirectUri = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null,
                uncheckedInvitationToken = null
            )
        }
    }

    private fun stubCurrentStep(
        session: InteractiveFlowSession,
        flow: InteractiveFlow,
        step: InteractiveFlowStep,
        redirectUri: URI
    ) {
        coEvery { engine.advance(session) } returns InteractiveFlowStepResult(session, step)
        coEvery { stepUriMapper.toRedirectUri(session, flow, step) } returns redirectUri
    }
}

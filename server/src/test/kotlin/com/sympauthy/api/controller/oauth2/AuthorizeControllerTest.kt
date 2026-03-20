package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNSUPPORTED_RESPONSE_TYPE
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class AuthorizeControllerTest {

    @MockK
    lateinit var webAuthorizationFlowManager: WebAuthorizationFlowManager

    @MockK
    lateinit var webFlowRedirectBuilder: WebAuthorizationFlowRedirectUriBuilder

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
                uncheckedCodeChallengeMethod = null
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
                uncheckedCodeChallengeMethod = null
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
                uncheckedCodeChallengeMethod = null
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
                uncheckedCodeChallengeMethod = null
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
                uncheckedCodeChallengeMethod = null
            )
        }
        assertEquals(UNSUPPORTED_RESPONSE_TYPE, exception.errorCode)
        assertEquals("authorize.response_type.invalid", exception.detailsId)
    }

    // --- Successful delegation ---

    @Test
    fun `authorize - Returns 307 redirect to sign-in URI on valid code request`() = runTest {
        val authorizeAttempt = mockk<AuthorizeAttempt>()
        val flow = mockk<WebAuthorizationFlow>()
        val signInUri = URI("https://auth.example.com/sign-in?state=abc")

        coEvery {
            webAuthorizationFlowManager.startAuthorizationWith(
                uncheckedClientId = "client",
                uncheckedClientState = "my-state",
                uncheckedClientNonce = null,
                uncheckedScopes = "openid profile",
                uncheckedRedirectUri = "https://example.com/callback",
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null
            )
        } returns (authorizeAttempt to flow)

        coEvery {
            webFlowRedirectBuilder.getSignInRedirectUri(
                authorizeAttempt = authorizeAttempt,
                flow = flow
            )
        } returns signInUri

        val result = controller.authorize(
            responseType = "code",
            uncheckedClientId = "client",
            uncheckedRedirectUri = "https://example.com/callback",
            uncheckedScopes = "openid profile",
            uncheckedClientState = "my-state",
            uncheckedClientNonce = null,
            uncheckedCodeChallenge = null,
            uncheckedCodeChallengeMethod = null
        )

        assertEquals(HttpStatus.TEMPORARY_REDIRECT, result.status)
        assertEquals(signInUri, result.header("Location")?.let { URI(it) })
    }

    @Test
    fun `authorize - Passes all query parameters to startAuthorizationWith`() = runTest {
        val authorizeAttempt = mockk<AuthorizeAttempt>()
        val flow = mockk<WebAuthorizationFlow>()
        val signInUri = URI("https://auth.example.com/sign-in?state=abc")

        coEvery {
            webAuthorizationFlowManager.startAuthorizationWith(
                uncheckedClientId = "my-client",
                uncheckedClientState = "my-state",
                uncheckedClientNonce = "my-nonce",
                uncheckedScopes = "openid email",
                uncheckedRedirectUri = "https://example.com/cb",
                uncheckedCodeChallenge = "challenge123",
                uncheckedCodeChallengeMethod = "S256"
            )
        } returns (authorizeAttempt to flow)

        coEvery {
            webFlowRedirectBuilder.getSignInRedirectUri(authorizeAttempt, flow)
        } returns signInUri

        controller.authorize(
            responseType = "code",
            uncheckedClientId = "my-client",
            uncheckedRedirectUri = "https://example.com/cb",
            uncheckedScopes = "openid email",
            uncheckedClientState = "my-state",
            uncheckedClientNonce = "my-nonce",
            uncheckedCodeChallenge = "challenge123",
            uncheckedCodeChallengeMethod = "S256"
        )

        coVerify(exactly = 1) {
            webAuthorizationFlowManager.startAuthorizationWith(
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
        val authorizeAttempt = mockk<AuthorizeAttempt>()
        val flow = mockk<WebAuthorizationFlow>()
        val signInUri = URI("https://auth.example.com/sign-in?state=abc")

        coEvery {
            webAuthorizationFlowManager.startAuthorizationWith(
                uncheckedClientId = "client",
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedScopes = null,
                uncheckedRedirectUri = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null
            )
        } returns (authorizeAttempt to flow)

        coEvery {
            webFlowRedirectBuilder.getSignInRedirectUri(authorizeAttempt, flow)
        } returns signInUri

        controller.authorize(
            responseType = "code",
            uncheckedClientId = "client",
            uncheckedRedirectUri = null,
            uncheckedScopes = null,
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedCodeChallenge = null,
            uncheckedCodeChallengeMethod = null
        )

        coVerify(exactly = 1) {
            webAuthorizationFlowManager.startAuthorizationWith(
                uncheckedClientId = "client",
                uncheckedClientState = null,
                uncheckedClientNonce = null,
                uncheckedScopes = null,
                uncheckedRedirectUri = null,
                uncheckedCodeChallenge = null,
                uncheckedCodeChallengeMethod = null
            )
        }
    }
}

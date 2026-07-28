package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.config.model.UrlsConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowRedirectUriBuilderTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var authorizationCodeManager: AuthorizationCodeManager

    @MockK
    lateinit var uncheckedUrlsConfig: UrlsConfig

    @SpyK
    @InjectMockKs
    lateinit var uriBuilder: WebAuthorizationFlowRedirectUriBuilder

    @Test
    fun `getRedirectUri - Redirect to collect claims step of the authorization flow if a claim is missing`() = runTest {
        val rawCollectClaimsUri = URI.create("https://www.example.com/collect-claims")
        val session = mockk<OnGoingInteractiveFlowSession>()
        val flow = mockk<WebAuthorizationFlow> {
            every { collectClaimsUri } returns rawCollectClaimsUri
        }
        val flowResult = WebAuthorizationFlowStatus(
            missingUser = false,
            missingRequiredClaims = true,
            missingMediaForClaimValidation = emptyList()
        )

        coEvery { uriBuilder.appendStateToUri(session, rawCollectClaimsUri) } returns rawCollectClaimsUri

        val result = uriBuilder.getRedirectUri(
            session = session,
            flow = flow,
            status = flowResult
        )

        assertEquals(rawCollectClaimsUri, result)
    }

    @Test
    fun `getRedirectUri - Redirect to code validation step of the authorization flow if a validation is required`() =
        runTest {
            val rawValidateCodeUri = URI.create("https://www.example.com/code")
            val session = mockk<OnGoingInteractiveFlowSession>()
            val flow = mockk<WebAuthorizationFlow> {
                every { validateClaimsUri } returns rawValidateCodeUri
            }
            val missingMedia = ValidationCodeMedia.EMAIL
            val flowResult = WebAuthorizationFlowStatus(
                missingUser = false,
                missingRequiredClaims = false,
                missingMediaForClaimValidation = listOf(missingMedia)
            )

            coEvery { uriBuilder.appendStateToUri(session, any()) } returnsArgument 1

            val result = uriBuilder.getRedirectUri(
                session = session,
                flow = flow,
                status = flowResult
            )

            assertEquals(rawValidateCodeUri.scheme, result.scheme)
            assertEquals(rawValidateCodeUri.host, result.host)
            assertEquals(rawValidateCodeUri.path, result.path)
            assertEquals("media=${missingMedia.name}", result.query)
        }

    @Test
    fun `getRedirectUri - Redirect to client if flow is complete`() = runTest {
        val rawClientUri = URI.create("https://www.example.com/callback")
        val session = mockk<CompletedInteractiveFlowSession>()
        val flow = mockk<WebAuthorizationFlow>()
        val flowResult = WebAuthorizationFlowStatus(
            missingUser = false,
            missingRequiredClaims = false,
            missingMediaForClaimValidation = emptyList()
        )

        coEvery { uriBuilder.getRedirectUriToClient(session) } returns rawClientUri

        val result = uriBuilder.getRedirectUri(
            session = session,
            flow = flow,
            status = flowResult
        )

        assertEquals(rawClientUri, result)
    }

    @Test
    fun `getRedirectUriToClient - Generate authorization code and append it to redirect uri passed by client`() =
        runTest {
            val clientRedirectUri = "https://www.example.com"
            val clientState = "clientState"
            val rawAuthorizationCode = "authorizationCode"
            val session = mockk<CompletedInteractiveFlowSession>()
            val oauth2 = InteractiveFlowSessionOAuth2(
                sessionId = UUID.randomUUID(),
                clientId = "test-client",
                redirectUri = clientRedirectUri,
                requestedScopes = emptyList(),
                state = clientState
            )
            val authorizationCode = mockk<AuthorizationCode> {
                every { code } returns rawAuthorizationCode
            }

            coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
            coEvery { authorizationCodeManager.generateCode(session) } returns authorizationCode

            val result = uriBuilder.getRedirectUriToClient(session)

            assertEquals("${clientRedirectUri}?state=${clientState}&code=${rawAuthorizationCode}", result.toString())
        }

    @Test
    fun `appendStateToUri - Encode state and add it as query param to uri`() = runTest {
        val uri = URI.create("https://www.example.com")
        val session = mockk<InteractiveFlowSession>()
        val encodedState = "encodedState"

        coEvery { sessionManager.encodeState(session) } returns encodedState

        val result = uriBuilder.appendStateToUri(
            session = session,
            uri = uri
        )

        assertEquals("https://www.example.com?state=encodedState", result.toString())
    }
}

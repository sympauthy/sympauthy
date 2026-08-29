package com.sympauthy.api.controller.client

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.resource.client.ClientProviderLinkInputResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.security.ClientAuthentication
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class ClientProviderLinkControllerTest {

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var clientRedirectUriManager: ClientRedirectUriManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var tokenManager: TokenManager

    @MockK
    lateinit var providerManager: ProviderManager

    @MockK
    lateinit var linkProviderManager: InteractiveFlowSessionLinkProviderManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @InjectMockKs
    lateinit var controller: ClientProviderLinkController

    private fun clientAuthentication(clientId: String): ClientAuthentication {
        val authenticationToken = mockk<AuthenticationToken> {
            every { this@mockk.clientId } returns clientId
        }
        return ClientAuthentication(authenticationToken, emptyList())
    }

    @Test
    fun `startLink - Validates token, provider and return URI, starts the session, returns state and redirect URL`() =
        runTest {
            val userId = UUID.randomUUID()
            val authentication = clientAuthentication("client-id")
            val client = mockk<Client> { every { id } returns "client-id" }
            val userToken = mockk<AuthenticationToken> { every { this@mockk.userId } returns userId }
            val returnUri = URI.create("https://client.example.com/linked")
            val flow = mockk<InteractiveFlow>()
            val session = mockk<OnGoingInteractiveFlowSession>()
            val steppedSession = mockk<OnGoingInteractiveFlowSession>()
            val nextUri = URI.create("https://auth.example.com/flow/confirm?state=abc")

            coEvery { clientManager.findClientById("client-id") } returns client
            coEvery { tokenManager.introspectToken(client, "user-access-token", "access_token") } returns userToken
            coEvery { providerManager.listEnabledProviders() } returns
                listOf(mockk<EnabledProvider> { every { id } returns "discord" })
            every {
                clientRedirectUriManager.parseRequestedRedirectUri(client, "https://client.example.com/linked", recoverable = true)
            } returns returnUri
            coEvery { interactiveAuthFlowSessionManager.getDefaultInteractiveFlow() } returns flow
            coEvery {
                linkProviderManager.startLinkProviderSession(userId, "discord", returnUri, flow, "client-id", null)
            } returns session
            coEvery { engine.advance(session) } returns
                InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.Confirm)
            coEvery { stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.Confirm) } returns nextUri
            coEvery { sessionManager.encodeState(steppedSession) } returns "encoded-state"

            val result = controller.startLink(
                authentication,
                "discord",
                ClientProviderLinkInputResource(
                    accessToken = "user-access-token",
                    returnUri = "https://client.example.com/linked"
                )
            )

            assertEquals("encoded-state", result.state)
            assertEquals(nextUri.toString(), result.redirectUrl)
        }

    @Test
    fun `startLink - Fails with invalid_access_token when the token is not bound to an end-user`() = runTest {
        val authentication = clientAuthentication("client-id")
        val client = mockk<Client>()
        val clientOnlyToken = mockk<AuthenticationToken> { every { userId } returns null }
        coEvery { clientManager.findClientById("client-id") } returns client
        coEvery { tokenManager.introspectToken(client, "client-token", "access_token") } returns clientOnlyToken

        val exception = assertThrows<BusinessException> {
            controller.startLink(
                authentication,
                "discord",
                ClientProviderLinkInputResource(
                    accessToken = "client-token",
                    returnUri = "https://client.example.com/linked"
                )
            )
        }

        assertEquals("client.providers.link.invalid_access_token", exception.detailsId)
        coVerify(exactly = 0) {
            linkProviderManager.startLinkProviderSession(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `startLink - Returns 404 and starts no session for an unknown provider`() = runTest {
        val userId = UUID.randomUUID()
        val authentication = clientAuthentication("client-id")
        val client = mockk<Client>()
        val userToken = mockk<AuthenticationToken> { every { this@mockk.userId } returns userId }
        coEvery { clientManager.findClientById("client-id") } returns client
        coEvery { tokenManager.introspectToken(client, "user-access-token", "access_token") } returns userToken
        // No enabled provider matches the path id -> orNotFound() -> 404, coherent with unknown user/client.
        coEvery { providerManager.listEnabledProviders() } returns emptyList()

        val exception = assertThrows<LocalizedHttpException> {
            controller.startLink(
                authentication,
                "bad-provider",
                ClientProviderLinkInputResource(
                    accessToken = "user-access-token",
                    returnUri = "https://client.example.com/linked"
                )
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        coVerify(exactly = 0) {
            linkProviderManager.startLinkProviderSession(any(), any(), any(), any(), any(), any())
        }
    }
}

package com.sympauthy.api.controller.client

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.resource.client.ClientMfaEnrollmentInputResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.config.model.DisabledMfaConfig
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.security.ClientAuthentication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
class ClientMfaEnrollmentControllerTest {

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var clientRedirectUriManager: ClientRedirectUriManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var tokenManager: TokenManager

    @MockK
    lateinit var mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    private fun controller(mfaConfig: MfaConfig) = ClientMfaEnrollmentController(
        interactiveAuthFlowSessionManager = interactiveAuthFlowSessionManager,
        clientRedirectUriManager = clientRedirectUriManager,
        clientManager = clientManager,
        tokenManager = tokenManager,
        mfaEnrollmentManager = mfaEnrollmentManager,
        engine = engine,
        stepUriMapper = stepUriMapper,
        sessionManager = sessionManager,
        uncheckedMfaConfig = mfaConfig,
    )

    private fun clientAuthentication(clientId: String): ClientAuthentication {
        val authenticationToken = mockk<AuthenticationToken> {
            every { this@mockk.clientId } returns clientId
        }
        return ClientAuthentication(authenticationToken, emptyList())
    }

    @Test
    fun `startEnrollment - Validates the token and return URI, starts the session, and returns state and redirect URL`() =
        runTest {
            val userId = UUID.randomUUID()
            val authentication = clientAuthentication("client-id")
            val client = mockk<Client>()
            val userToken = mockk<AuthenticationToken> {
                every { this@mockk.userId } returns userId
            }
            val returnUri = URI.create("https://client.example.com/done")
            val flow = mockk<InteractiveFlow>()
            val session = mockk<OnGoingInteractiveFlowSession>()
            val steppedSession = mockk<OnGoingInteractiveFlowSession>()
            val enrollUri = URI.create("https://auth.example.com/flow/mfa/enrollment?state=abc")

            coEvery { clientManager.findClientById("client-id") } returns client
            every { client.id } returns "client-id"
            coEvery { tokenManager.introspectToken(client, "user-access-token", "access_token") } returns userToken
            every {
                clientRedirectUriManager.parseRequestedRedirectUri(client, "https://client.example.com/done", recoverable = true)
            } returns returnUri
            coEvery { interactiveAuthFlowSessionManager.getDefaultInteractiveFlow() } returns flow
            coEvery {
                mfaEnrollmentManager.startMfaEnrollmentSession(userId, returnUri, flow, "client-id")
            } returns session
            coEvery { engine.advance(session) } returns
                InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.MfaSelectionForEnrollment)
            coEvery {
                stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.MfaSelectionForEnrollment)
            } returns enrollUri
            coEvery { sessionManager.encodeState(steppedSession) } returns "encoded-state"

            val result = controller(EnabledMfaConfig(totp = true, required = false)).startEnrollment(
                authentication,
                ClientMfaEnrollmentInputResource(
                    accessToken = "user-access-token",
                    returnUri = "https://client.example.com/done"
                )
            )

            assertEquals("encoded-state", result.state)
            assertEquals(enrollUri.toString(), result.redirectUrl)
        }

    @Test
    fun `startEnrollment - Validates and forwards the optional cancel URI`() = runTest {
        val userId = UUID.randomUUID()
        val authentication = clientAuthentication("client-id")
        val client = mockk<Client>()
        val userToken = mockk<AuthenticationToken> {
            every { this@mockk.userId } returns userId
        }
        val returnUri = URI.create("https://client.example.com/done")
        val cancelUri = URI.create("https://client.example.com/cancelled")
        val flow = mockk<InteractiveFlow>()
        val session = mockk<OnGoingInteractiveFlowSession>()
        val steppedSession = mockk<OnGoingInteractiveFlowSession>()
        val enrollUri = URI.create("https://auth.example.com/flow/mfa/enrollment?state=abc")

        coEvery { clientManager.findClientById("client-id") } returns client
        every { client.id } returns "client-id"
        coEvery { tokenManager.introspectToken(client, "user-access-token", "access_token") } returns userToken
        every {
            clientRedirectUriManager.parseRequestedRedirectUri(client, "https://client.example.com/done", recoverable = true)
        } returns returnUri
        every {
            clientRedirectUriManager.parseRequestedRedirectUri(client, "https://client.example.com/cancelled", recoverable = true)
        } returns cancelUri
        coEvery { interactiveAuthFlowSessionManager.getDefaultInteractiveFlow() } returns flow
        // The stub only matches (and thus drives the redirect) when the validated cancel URI is threaded through.
        coEvery {
            mfaEnrollmentManager.startMfaEnrollmentSession(userId, returnUri, flow, "client-id", cancelUri)
        } returns session
        coEvery { engine.advance(session) } returns
            InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.MfaSelectionForEnrollment)
        coEvery {
            stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.MfaSelectionForEnrollment)
        } returns enrollUri
        coEvery { sessionManager.encodeState(steppedSession) } returns "encoded-state"

        val result = controller(EnabledMfaConfig(totp = true, required = false)).startEnrollment(
            authentication,
            ClientMfaEnrollmentInputResource(
                accessToken = "user-access-token",
                returnUri = "https://client.example.com/done",
                cancelUri = "https://client.example.com/cancelled"
            )
        )

        assertEquals(enrollUri.toString(), result.redirectUrl)
    }

    @Test
    fun `startEnrollment - Fails with mfa_disabled and starts no session when MFA is disabled`() = runTest {
        val authentication = clientAuthentication("client-id")

        val exception = assertThrows<BusinessException> {
            controller(mockk<DisabledMfaConfig>()).startEnrollment(
                authentication,
                ClientMfaEnrollmentInputResource(
                    accessToken = "user-access-token",
                    returnUri = "https://client.example.com/done"
                )
            )
        }

        assertEquals("client.mfa.enrollment.mfa_disabled", exception.detailsId)
        coVerify(exactly = 0) { mfaEnrollmentManager.startMfaEnrollmentSession(any(), any(), any(), any()) }
    }

    @Test
    fun `startEnrollment - Fails with invalid_access_token when the token cannot be introspected`() = runTest {
        val authentication = clientAuthentication("client-id")
        val client = mockk<Client>()

        coEvery { clientManager.findClientById("client-id") } returns client
        coEvery { tokenManager.introspectToken(client, "bad-token", "access_token") } returns null

        val exception = assertThrows<BusinessException> {
            controller(EnabledMfaConfig(totp = true, required = false)).startEnrollment(
                authentication,
                ClientMfaEnrollmentInputResource(
                    accessToken = "bad-token",
                    returnUri = "https://client.example.com/done"
                )
            )
        }

        assertEquals("client.mfa.enrollment.invalid_access_token", exception.detailsId)
        coVerify(exactly = 0) { mfaEnrollmentManager.startMfaEnrollmentSession(any(), any(), any(), any()) }
    }

    @Test
    fun `startEnrollment - Fails with invalid_access_token when the token is not associated with an end-user`() =
        runTest {
            val authentication = clientAuthentication("client-id")
            val client = mockk<Client>()
            val clientOnlyToken = mockk<AuthenticationToken> {
                every { userId } returns null
            }

            coEvery { clientManager.findClientById("client-id") } returns client
            coEvery { tokenManager.introspectToken(client, "client-token", "access_token") } returns clientOnlyToken

            val exception = assertThrows<BusinessException> {
                controller(EnabledMfaConfig(totp = true, required = false)).startEnrollment(
                    authentication,
                    ClientMfaEnrollmentInputResource(
                        accessToken = "client-token",
                        returnUri = "https://client.example.com/done"
                    )
                )
            }

            assertEquals("client.mfa.enrollment.invalid_access_token", exception.detailsId)
            coVerify(exactly = 0) { mfaEnrollmentManager.startMfaEnrollmentSession(any(), any(), any(), any()) }
        }
}

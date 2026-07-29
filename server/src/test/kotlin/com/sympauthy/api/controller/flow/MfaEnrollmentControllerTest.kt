package com.sympauthy.api.controller.flow

import com.sympauthy.api.resource.flow.MfaEnrollmentInputResource
import com.sympauthy.business.manager.ClientManager
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
import com.sympauthy.security.UserAuthentication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class MfaEnrollmentControllerTest {

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @InjectMockKs
    lateinit var controller: MfaEnrollmentController

    @Test
    fun `startEnrollment - Validates the return URI, starts the session, and returns the state and redirect URL`() =
        runTest {
            val userId = UUID.randomUUID()
            val authenticationToken = mockk<AuthenticationToken> {
                every { this@mockk.userId } returns userId
                every { clientId } returns "client-id"
            }
            val authentication = UserAuthentication(authenticationToken, emptyList(), emptyList())
            val client = mockk<Client>()
            val returnUri = URI.create("https://client.example.com/done")
            val flow = mockk<InteractiveFlow>()
            val session = mockk<OnGoingInteractiveFlowSession>()
            val steppedSession = mockk<OnGoingInteractiveFlowSession>()
            val enrollUri = URI.create("https://auth.example.com/flow/mfa/totp/enroll?state=abc")

            coEvery { clientManager.findClientById("client-id") } returns client
            every {
                interactiveAuthFlowSessionManager.parseRequestedRedirectUri(client, "https://client.example.com/done")
            } returns returnUri
            coEvery { interactiveAuthFlowSessionManager.getDefaultInteractiveFlow() } returns flow
            coEvery { mfaEnrollmentManager.startMfaEnrollmentSession(userId, returnUri, flow) } returns session
            coEvery { engine.getCurrentStep(session) } returns
                InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.MfaTotpEnroll)
            coEvery {
                stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.MfaTotpEnroll)
            } returns enrollUri
            coEvery { sessionManager.encodeState(steppedSession) } returns "encoded-state"

            val result = controller.startEnrollment(
                authentication,
                MfaEnrollmentInputResource(returnUri = "https://client.example.com/done")
            )

            assertEquals("encoded-state", result.state)
            assertEquals(enrollUri.toString(), result.redirectUrl)
        }
}

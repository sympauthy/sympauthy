package com.sympauthy.api.controller.flow

import com.sympauthy.api.mapper.flow.FlowErrorResourceMapper
import com.sympauthy.api.resource.flow.FlowErrorResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.security.StateAuthentication
import io.micronaut.http.HttpRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ErrorControllerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    @MockK
    lateinit var flowErrorResourceMapper: FlowErrorResourceMapper

    @InjectMockKs
    lateinit var controller: ErrorController

    @Test
    fun `getError - Passes the values of a failed session to the mapper that renders its messages`() = runTest {
        val request = mockk<HttpRequest<*>> {
            every { locale } returns Optional.of(Locale.US)
        }
        val session = FailedInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.of(2026, 8, 31, 10, 0),
            errorDetailsId = "auth.interactive_flow_session.validate.expired",
            errorDescriptionId = "description.oauth2.expired",
            errorValues = mapOf("expirationDate" to "2026-08-31T10:00"),
            errorDate = LocalDateTime.of(2026, 8, 31, 10, 0),
        )
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)
        // Only an exception carrying the session's values is answered, so reaching the assertion is
        // what proves they were not dropped on the way.
        every {
            flowErrorResourceMapper.toResource(
                match<BusinessException> { it.values == mapOf("expirationDate" to "2026-08-31T10:00") },
                Locale.US
            )
        } returns FlowErrorResource(errorCode = "auth.interactive_flow_session.validate.expired")

        val result = controller.getError(request, StateAuthentication("encoded-state"))

        assertEquals("auth.interactive_flow_session.validate.expired", result.errorCode)
    }

    @Test
    fun `getError - Passes the values of a state verification failure to the mapper`() = runTest {
        val request = mockk<HttpRequest<*>> {
            every { locale } returns Optional.of(Locale.US)
        }
        val sessionId = UUID.randomUUID().toString()
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            FailedVerifyEncodedStateResult(
                detailsId = "auth.interactive_flow_session.validate.missing_session",
                descriptionId = "description.oauth2.expired",
                values = mapOf("sessionId" to sessionId)
            )
        every {
            flowErrorResourceMapper.toResource(
                match<BusinessException> { it.values == mapOf("sessionId" to sessionId) },
                Locale.US
            )
        } returns FlowErrorResource(errorCode = "auth.interactive_flow_session.validate.missing_session")

        val result = controller.getError(request, StateAuthentication("encoded-state"))

        assertEquals("auth.interactive_flow_session.validate.missing_session", result.errorCode)
    }

    @Test
    fun `getError - Returns a redirect instead of an error body when the session is still ongoing`() = runTest {
        val redirectUri = URI.create("https://flow.example.com/sign-in?state=encoded-state")
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { flowId } returns "flow-id"
        }
        val steppedSession = mockk<OnGoingInteractiveFlowSession>()
        val flow = mockk<InteractiveFlow>()
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)
        every { interactiveAuthFlowSessionManager.findById("flow-id") } returns flow
        coEvery { engine.advance(session) } returns
            InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.SignIn)
        coEvery { stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.SignIn) } returns redirectUri
        every { flowErrorResourceMapper.toResource(redirectUri) } returns
            FlowErrorResource(redirectUrl = redirectUri.toString())

        val result = controller.getError(mockk<HttpRequest<*>>(), StateAuthentication("encoded-state"))

        assertEquals(redirectUri.toString(), result.redirectUrl)
        assertNull(result.errorCode)
    }
}

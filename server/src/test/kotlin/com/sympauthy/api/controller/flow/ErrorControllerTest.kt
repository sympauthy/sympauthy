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
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.security.StateAuthentication
import io.micronaut.http.HttpRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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

    private val controller by lazy {
        ErrorController(
            sessionManager,
            interactiveAuthFlowSessionManager,
            engine,
            stepUriMapper,
            flowErrorResourceMapper
        )
    }

    private val request = mockk<HttpRequest<*>> {
        every { locale } returns Optional.of(Locale.US)
    }

    @Test
    fun `getError - Renders the failed session with the values its message interpolates`() = runTest {
        val session = FailedInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().minusMinutes(1),
            errorDetailsId = "auth.interactive_flow_session.validate.expired",
            errorDescriptionId = "description.oauth2.expired",
            errorValues = mapOf("expirationDate" to "2026-08-31T10:00:00"),
            errorDate = LocalDateTime.now().minusMinutes(1),
        )
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)
        val exception = slot<BusinessException>()
        every { flowErrorResourceMapper.toResource(capture(exception), Locale.US) } returns
            FlowErrorResource(errorCode = session.errorDetailsId)

        controller.getError(request, StateAuthentication("encoded-state"))

        assertEquals(mapOf("expirationDate" to "2026-08-31T10:00:00"), exception.captured.values)
    }

    @Test
    fun `getError - Renders the verification failure with the values its message interpolates`() = runTest {
        val sessionId = UUID.randomUUID().toString()
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            FailedVerifyEncodedStateResult(
                detailsId = "auth.interactive_flow_session.validate.missing_session",
                descriptionId = "description.oauth2.expired",
                values = mapOf("sessionId" to sessionId)
            )
        val exception = slot<BusinessException>()
        every { flowErrorResourceMapper.toResource(capture(exception), Locale.US) } returns
            FlowErrorResource(errorCode = "auth.interactive_flow_session.validate.missing_session")

        controller.getError(request, StateAuthentication("encoded-state"))

        assertEquals(mapOf("sessionId" to sessionId), exception.captured.values)
    }

    @Test
    fun `getError - Renders a failure that carries no value with an empty value map`() = runTest {
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            FailedVerifyEncodedStateResult(
                detailsId = "auth.interactive_flow_session.validate.missing_state",
                descriptionId = "description.oauth2.invalid_state"
            )
        val exception = slot<BusinessException>()
        every { flowErrorResourceMapper.toResource(capture(exception), Locale.US) } returns
            FlowErrorResource(errorCode = "auth.interactive_flow_session.validate.missing_state")

        controller.getError(request, StateAuthentication("encoded-state"))

        assertEquals(emptyMap<String, String>(), exception.captured.values)
    }
}

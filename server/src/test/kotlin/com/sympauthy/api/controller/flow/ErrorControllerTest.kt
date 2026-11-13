package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.mapper.flow.ClientReferenceResourceMapper
import com.sympauthy.api.mapper.flow.FlowErrorResourceMapper
import com.sympauthy.api.resource.flow.ClientReferenceResource
import com.sympauthy.api.resource.flow.FlowErrorResource
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.security.observedRequestOf
import com.sympauthy.security.StateAuthentication
import io.micronaut.http.HttpRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class ErrorControllerTest {

    @MockK
    lateinit var interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil

    @MockK
    lateinit var flowErrorResourceMapper: FlowErrorResourceMapper

    @MockK
    lateinit var clientMapper: ClientReferenceResourceMapper

    private val controller by lazy {
        ErrorController(interactiveAuthFlowSessionControllerUtil, flowErrorResourceMapper, clientMapper)
    }

    private val observed = observedRequestOf()

    @Test
    fun `getError - Renders the failure the helper answers in the locale the request asks for`() = runTest {
        val request = mockk<HttpRequest<*>> {
            every { locale } returns Optional.of(Locale.FRANCE)
        }
        val failure = businessExceptionOf("flow.web.invalid_flow")
        val session = mockk<InteractiveFlowSession>()
        val client = ClientReferenceResource(id = "billing-web", displayName = "Billing portal")
        // Matching only the observed request the controller bound is what proves it was threaded through.
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchSessionThenAnswerFailureOrRedirect<FlowErrorResource>(
                eq("encoded-state"), refEq(observed), any(), any()
            )
        } coAnswers {
            arg<suspend (BusinessException, InteractiveFlowSession?) -> FlowErrorResource>(2)(failure, session)
        }
        coEvery { clientMapper.toResourceOrNull(session) } returns client
        every { flowErrorResourceMapper.toResource(failure, Locale.FRANCE, client) } returns
            FlowErrorResource(errorCode = "flow.web.invalid_flow", client = client)

        val result = controller.getError(request, StateAuthentication("encoded-state"), observed)

        assertEquals("flow.web.invalid_flow", result.errorCode)
        assertEquals(client, result.client)
    }

    @Test
    fun `getError - Names no client where the state resolves to no session`() = runTest {
        val request = mockk<HttpRequest<*>> {
            every { locale } returns Optional.of(Locale.FRANCE)
        }
        val failure = businessExceptionOf("auth.interactive_flow_session.validate.missing_session")
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchSessionThenAnswerFailureOrRedirect<FlowErrorResource>(
                eq("encoded-state"), refEq(observed), any(), any()
            )
        } coAnswers {
            arg<suspend (BusinessException, InteractiveFlowSession?) -> FlowErrorResource>(2)(failure, null)
        }
        every { flowErrorResourceMapper.toResource(failure, Locale.FRANCE, null) } returns
            FlowErrorResource(errorCode = "auth.interactive_flow_session.validate.missing_session")

        val result = controller.getError(request, StateAuthentication("encoded-state"), observed)

        assertNull(result.client)
    }

    @Test
    fun `getError - Answers the redirect the helper answers with only redirect_url`() = runTest {
        val redirectUri = URI.create("https://flow.example.com/sign-in?state=encoded-state")
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchSessionThenAnswerFailureOrRedirect<FlowErrorResource>(
                eq("encoded-state"), refEq(observed), any(), any()
            )
        } coAnswers {
            arg<suspend (URI) -> FlowErrorResource>(3)(redirectUri)
        }
        every { flowErrorResourceMapper.toResource(redirectUri) } returns
            FlowErrorResource(redirectUrl = redirectUri.toString())

        val result = controller.getError(mockk(), StateAuthentication("encoded-state"), observed)

        assertEquals(redirectUri.toString(), result.redirectUrl)
    }
}

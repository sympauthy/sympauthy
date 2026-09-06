package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.resource.flow.SimpleFlowResource
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.security.StateAuthentication
import io.micronaut.http.HttpMethod
import io.micronaut.http.simple.SimpleHttpRequest
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class CancelControllerTest {

    private val httpRequest = SimpleHttpRequest<Any>(HttpMethod.GET, "http://198.51.100.10/", null)

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil

    private val controller by lazy {
        CancelController(sessionManager, interactiveAuthFlowSessionControllerUtil)
    }

    @Test
    fun `cancel - Marks the session as cancelled and returns the cancel redirect URL`() = runTest {
        val ongoingSession = mockk<OnGoingInteractiveFlowSession>()
        val flow = mockk<InteractiveFlow>()
        val cancelledSession = mockk<CancelledInteractiveFlowSession>()
        val cancelUri = URI.create("https://client.example.com/callback?error=access_denied")

        coEvery { sessionManager.markAsCancelled(ongoingSession) } returns cancelledSession
        // Exercise the update + redirect lambdas the controller passes to the shared plumbing.
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenUpdateAndRedirect<SimpleFlowResource>(
                eq("encoded-state"), any(), any(), any()
            )
        } coAnswers {
            val update = arg<suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> Any>(2)
            val mapRedirectUriToResource = arg<suspend (URI) -> SimpleFlowResource>(3)
            assertSame(cancelledSession, update(ongoingSession, flow))
            mapRedirectUriToResource(cancelUri)
        }

        val result = controller.cancel(StateAuthentication("encoded-state"), httpRequest)

        assertEquals(cancelUri.toString(), result.redirectUrl)
    }
}

package com.sympauthy.api.flow

import com.sympauthy.api.util.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.business.model.securitycontext.edge.observedRequestOf
import com.sympauthy.security.StateAuthentication
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class CancelControllerTest {

    @MockK
    lateinit var interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil

    private val controller by lazy {
        CancelController(interactiveAuthFlowSessionControllerUtil)
    }

    @Test
    fun `cancel - Redirects the browser where leaving the session sends it`() = runTest {
        val observed = observedRequestOf()
        val leaveUri = URI.create("https://client.example.com/callback?error=access_denied")
        // Matching only the observed request the controller bound is what proves it was threaded through.
        coEvery {
            interactiveAuthFlowSessionControllerUtil.fetchSessionThenLeave("encoded-state", refEq(observed))
        } returns leaveUri

        val result = controller.cancel(StateAuthentication("encoded-state"), observed)

        assertEquals(HttpStatus.SEE_OTHER, result.status)
        assertEquals(leaveUri.toString(), result.header(HttpHeaders.LOCATION))
    }
}

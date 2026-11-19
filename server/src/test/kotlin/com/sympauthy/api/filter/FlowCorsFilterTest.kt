package com.sympauthy.api.filter

import com.sympauthy.api.controller.FLOW_SURFACE
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.config.model.EnabledAuthorizationFlowsConfig
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.filter.ServerFilterPhase
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.reactivex.rxjava3.core.Flowable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@ExtendWith(MockKExtension::class)
class FlowCorsFilterTest {

    @MockK
    lateinit var corsPreflightHeaders: CorsPreflightHeaders

    @MockK
    lateinit var chain: ServerFilterChain

    private lateinit var filter: FlowCorsFilter

    /**
     * Answered with a refusal, which the security filter writes for a request carrying no state.
     */
    private val chainResponse: MutableHttpResponse<*> = HttpResponse.unauthorized<Any>()

    @BeforeEach
    fun setUp() {
        val flow = InteractiveFlow(
            id = "default",
            signInUri = URI.create("$FLOW_ORIGIN/sign-in"),
            collectClaimsUri = URI.create("$FLOW_ORIGIN/claims"),
            validateClaimsUri = URI.create("$FLOW_ORIGIN/validate"),
            errorUri = URI.create("$OTHER_FLOW_ORIGIN/error")
        )
        filter = FlowCorsFilter(EnabledAuthorizationFlowsConfig(flow, emptyList()), corsPreflightHeaders)
    }

    @Test
    fun `doFilter - Hand a request with no Origin on untouched`() {
        val request = HttpRequest.GET<Any>(PATH)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = filter(request)

        assertSame(chainResponse, response)
        assertNull(response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @ParameterizedTest
    @ValueSource(strings = [FLOW_ORIGIN, OTHER_FLOW_ORIGIN])
    fun `doFilter - Answer a preflight from the origin of a flow's page with the preflight headers`(origin: String) {
        every { corsPreflightHeaders.addTo(any(), ALLOWED_METHODS) } answers {
            firstArg<MutableHttpResponse<*>>().header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HEADERS)
        }

        val response = filter(preflight(origin))

        assertEquals(HttpStatus.OK, response.status)
        assertEquals(origin, response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertEquals(listOf(HttpHeaders.ORIGIN), response.headers.getAll(HttpHeaders.VARY))
        assertEquals(ALLOWED_HEADERS, response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `doFilter - Answer a preflight from any other origin with a bare 200`() {
        val response = filter(preflight(UNKNOWN_ORIGIN))

        assertEquals(HttpStatus.OK, response.status)
        assertEquals(emptyList<String>(), response.headers.names().toList())
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `doFilter - Add the origin and Vary to the response to a request from the origin of a flow's page`() {
        val request = HttpRequest.GET<Any>(PATH).header(HttpHeaders.ORIGIN, FLOW_ORIGIN)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = filter(request)

        assertSame(chainResponse, response)
        assertEquals(FLOW_ORIGIN, response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertEquals(listOf(HttpHeaders.ORIGIN), response.headers.getAll(HttpHeaders.VARY))
    }

    @Test
    fun `doFilter - Add nothing to the response to a request from any other origin`() {
        val request = HttpRequest.GET<Any>(PATH).header(HttpHeaders.ORIGIN, UNKNOWN_ORIGIN)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = filter(request)

        assertSame(chainResponse, response)
        assertEquals(emptyList<String>(), response.headers.names().toList())
    }

    @Test
    fun `getOrder - Run ahead of the security filter`() {
        assertTrue(filter.order < ServerFilterPhase.SECURITY.order())
    }

    private fun preflight(origin: String) = HttpRequest.OPTIONS<Any>(PATH)
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name)

    private fun filter(request: HttpRequest<*>): MutableHttpResponse<*> =
        Flowable.fromPublisher(filter.doFilter(request, chain)).blockingSingle()

    private companion object {

        const val PATH = "$FLOW_SURFACE/sign-in"
        const val FLOW_ORIGIN = "https://flow.example.com"

        /** On a port of its own, which is part of the origin. */
        const val OTHER_FLOW_ORIGIN = "http://localhost:5173"
        const val UNKNOWN_ORIGIN = "https://evil.example.com"
        const val ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS"
        const val ALLOWED_HEADERS = "Content-Type, X-Preflight-Double"
    }
}

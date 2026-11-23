package com.sympauthy.api.operator.admin

import com.sympauthy.api.ADMIN_SURFACE
import com.sympauthy.api.filter.CorsPreflightHeaders
import com.sympauthy.config.model.EnabledAdminConfig
import com.sympauthy.config.model.EnabledUrlsConfig
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class AdminCorsFilterTest {

    @MockK
    lateinit var corsPreflightHeaders: CorsPreflightHeaders

    @MockK
    lateinit var chain: ServerFilterChain

    /**
     * Answered with a refusal, which the security filter writes for a request carrying no token.
     */
    private val chainResponse: MutableHttpResponse<*> = HttpResponse.unauthorized<Any>()

    @Test
    fun `doFilter - Hand a request with no Origin on untouched`() {
        val request = HttpRequest.GET<Any>(PATH)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = integratedFilter().filter(request)

        assertSame(chainResponse, response)
        assertNull(response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun `doFilter - Answer a preflight from the root origin of an integrated interface with the preflight headers`() {
        stubPreflightHeaders()

        val response = integratedFilter().filter(preflight(ROOT_ORIGIN))

        assertEquals(HttpStatus.OK, response.status)
        assertEquals(ROOT_ORIGIN, response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertEquals(listOf(HttpHeaders.ORIGIN), response.headers.getAll(HttpHeaders.VARY))
        assertEquals(ALLOWED_HEADERS, response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `doFilter - Answer a preflight from any other origin of an integrated interface with a bare 200`() {
        val response = integratedFilter().filter(preflight(UNKNOWN_ORIGIN))

        assertEquals(HttpStatus.OK, response.status)
        assertEquals(emptyList<String>(), response.headers.names().toList())
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `doFilter - Add the origin, Vary and the exposed challenge to a response to the root origin`() {
        val request = HttpRequest.GET<Any>(PATH).header(HttpHeaders.ORIGIN, ROOT_ORIGIN)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = integratedFilter().filter(request)

        assertSame(chainResponse, response)
        assertEquals(ROOT_ORIGIN, response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertEquals(listOf(HttpHeaders.ORIGIN), response.headers.getAll(HttpHeaders.VARY))
        assertEquals(HttpHeaders.WWW_AUTHENTICATE, response.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
    }

    @Test
    fun `doFilter - Add nothing to a response to any other origin of an integrated interface`() {
        val request = HttpRequest.GET<Any>(PATH).header(HttpHeaders.ORIGIN, UNKNOWN_ORIGIN)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = integratedFilter().filter(request)

        assertSame(chainResponse, response)
        assertEquals(emptyList<String>(), response.headers.names().toList())
    }

    @Test
    fun `doFilter - Answer a preflight to an external interface with every origin and no Vary`() {
        stubPreflightHeaders()

        val response = externalFilter().filter(preflight(UNKNOWN_ORIGIN))

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("*", response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertNull(response.header(HttpHeaders.VARY))
        assertEquals(ALLOWED_HEADERS, response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `doFilter - Add every origin and the exposed challenge to a response to an external interface`() {
        val request = HttpRequest.GET<Any>(PATH).header(HttpHeaders.ORIGIN, UNKNOWN_ORIGIN)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = externalFilter().filter(request)

        assertSame(chainResponse, response)
        assertEquals("*", response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertNull(response.header(HttpHeaders.VARY))
        assertEquals(HttpHeaders.WWW_AUTHENTICATE, response.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
    }

    @Test
    fun `doFilter - Hand a preflight on untouched when the administration API is disabled`() {
        val request = preflight(ROOT_ORIGIN)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = disabledFilter().filter(request)

        assertSame(chainResponse, response)
        assertEquals(emptyList<String>(), response.headers.names().toList())
    }

    @Test
    fun `doFilter - Hand a request on untouched when the administration API is disabled`() {
        val request = HttpRequest.GET<Any>(PATH).header(HttpHeaders.ORIGIN, ROOT_ORIGIN)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = disabledFilter().filter(request)

        assertSame(chainResponse, response)
        assertEquals(emptyList<String>(), response.headers.names().toList())
    }

    @Test
    fun `getOrder - Run ahead of the security filter`() {
        assertTrue(integratedFilter().order < ServerFilterPhase.SECURITY.order())
    }

    private fun integratedFilter() = filterOf(enabled = true, integratedUi = true)

    private fun externalFilter() = filterOf(enabled = true, integratedUi = false)

    private fun disabledFilter() = filterOf(enabled = false, integratedUi = true)

    private fun filterOf(enabled: Boolean, integratedUi: Boolean) = AdminCorsFilter(
        EnabledAdminConfig(enabled = enabled, integratedUi = integratedUi, audienceId = "admin"),
        EnabledUrlsConfig(root = URI.create("$ROOT_ORIGIN/sympauthy")),
        corsPreflightHeaders
    )

    private fun stubPreflightHeaders() {
        every { corsPreflightHeaders.addTo(any(), ALLOWED_METHODS) } answers {
            firstArg<MutableHttpResponse<*>>().header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HEADERS)
        }
    }

    private fun preflight(origin: String) = HttpRequest.OPTIONS<Any>(PATH)
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)

    private fun AdminCorsFilter.filter(request: HttpRequest<*>): MutableHttpResponse<*> =
        Flowable.fromPublisher(doFilter(request, chain)).blockingSingle()

    private companion object {

        const val PATH = "$ADMIN_SURFACE/clients"

        /** The origin of `urls.root`, whose port is part of it and whose path is not. */
        const val ROOT_ORIGIN = "https://auth.example.com:8443"
        const val UNKNOWN_ORIGIN = "https://evil.example.com"
        const val ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS"
        const val ALLOWED_HEADERS = "Content-Type, X-Preflight-Double"
    }
}

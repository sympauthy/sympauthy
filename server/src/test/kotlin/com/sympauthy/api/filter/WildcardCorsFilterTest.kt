package com.sympauthy.api.filter

import com.sympauthy.api.OAUTH2_SURFACE
import com.sympauthy.api.OPENID_SURFACE
import com.sympauthy.api.USER_SURFACE
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.filter.ServerFilterPhase
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@ExtendWith(MockKExtension::class)
class WildcardCorsFilterTest {

    @MockK
    lateinit var corsPreflightHeaders: CorsPreflightHeaders

    @MockK
    lateinit var chain: ServerFilterChain

    @InjectMockKs
    lateinit var filter: WildcardCorsFilter

    /**
     * Answered with a refusal, which the security filter writes for a request carrying no token.
     */
    private val chainResponse: MutableHttpResponse<*> = HttpResponse.unauthorized<Any>()

    @Test
    fun `doFilter - Hand a request with no Origin on untouched`() {
        val request = HttpRequest.GET<Any>(USER_INFO_PATH)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = filter(request)

        assertSame(chainResponse, response)
        assertNull(response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @ParameterizedTest
    @CsvSource(
        "$USER_SURFACE/claims, 'GET, PATCH, OPTIONS'",
        "/branding/brand.json, 'GET, OPTIONS'",
        "$OPENID_SURFACE/userinfo, 'GET, POST, OPTIONS'",
        "$OAUTH2_SURFACE/token, 'GET, POST, OPTIONS'",
        "/.well-known/openid-configuration, 'GET, POST, OPTIONS'",
    )
    fun `doFilter - Answer a preflight with every origin and the methods of the surface it reached`(
        path: String,
        allowedMethods: String
    ) {
        every { corsPreflightHeaders.addTo(any(), allowedMethods) } answers {
            firstArg<MutableHttpResponse<*>>().header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HEADERS)
        }
        val request = HttpRequest.OPTIONS<Any>(path)
            .header(HttpHeaders.ORIGIN, ANY_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name)

        val response = filter(request)

        assertEquals(HttpStatus.OK, response.status)
        assertEquals("*", response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertEquals(ALLOWED_HEADERS, response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
        assertNull(response.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `doFilter - Add every origin and the exposed challenge to the response to a request with an Origin`() {
        val request = HttpRequest.GET<Any>(USER_INFO_PATH).header(HttpHeaders.ORIGIN, ANY_ORIGIN)
        every { chain.proceed(request) } returns Flowable.just(chainResponse)

        val response = filter(request)

        assertSame(chainResponse, response)
        assertEquals("*", response.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        assertNull(response.header(HttpHeaders.VARY))
        assertEquals(HttpHeaders.WWW_AUTHENTICATE, response.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
    }

    @Test
    fun `getOrder - Run ahead of the security filter`() {
        assertTrue(filter.order < ServerFilterPhase.SECURITY.order())
    }

    private fun filter(request: HttpRequest<*>): MutableHttpResponse<*> =
        Flowable.fromPublisher(filter.doFilter(request, chain)).blockingSingle()

    private companion object {

        const val USER_INFO_PATH = "$OPENID_SURFACE/userinfo"
        const val ANY_ORIGIN = "https://some-app.example.com"
        const val ALLOWED_HEADERS = "Content-Type, X-Preflight-Double"
    }
}

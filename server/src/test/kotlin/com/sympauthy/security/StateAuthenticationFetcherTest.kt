package com.sympauthy.security

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class StateAuthenticationFetcherTest {

    private val fetcher = StateAuthenticationFetcher()

    @Test
    fun `GET to flow path with state param returns StateAuthentication with state`() = runTest {
        val state = "test-jwt"
        val request = mockk<HttpRequest<*>>()
        every { request.path } returns "/api/v1/flow/sign-in"
        every { request.method } returns HttpMethod.GET
        every { request.parameters["state"] } returns state

        val result = fetcher.fetchAuthentication(request).asFlow().toList()

        assertEquals(1, result.size)
        assertEquals(state, (result[0] as StateAuthentication).state)
    }

    @Test
    fun `GET to flow path without state returns StateAuthentication with null state`() = runTest {
        val request = mockk<HttpRequest<*>>()
        every { request.path } returns "/api/v1/flow/sign-in"
        every { request.method } returns HttpMethod.GET
        every { request.parameters["state"] } returns null

        val result = fetcher.fetchAuthentication(request).asFlow().toList()

        assertEquals(1, result.size)
        assertNull((result[0] as StateAuthentication).state)
    }

    @Test
    fun `POST to flow path with State authorization header returns StateAuthentication with state`() = runTest {
        val state = "test-jwt"
        val request = mockk<HttpRequest<*>>()
        every { request.path } returns "/api/v1/flow/sign-in"
        every { request.method } returns HttpMethod.POST
        every { request.headers.authorization } returns Optional.of("State $state")

        val result = fetcher.fetchAuthentication(request).asFlow().toList()

        assertEquals(1, result.size)
        assertEquals(state, (result[0] as StateAuthentication).state)
    }

    @Test
    fun `POST to flow path with Bearer authorization header returns StateAuthentication with null state`() = runTest {
        val request = mockk<HttpRequest<*>>()
        every { request.path } returns "/api/v1/flow/sign-in"
        every { request.method } returns HttpMethod.POST
        every { request.headers.authorization } returns Optional.of("Bearer other-jwt")

        val result = fetcher.fetchAuthentication(request).asFlow().toList()

        assertEquals(1, result.size)
        assertNull((result[0] as StateAuthentication).state)
    }

    @Test
    fun `POST to flow path without Authorization header returns StateAuthentication with null state`() = runTest {
        val request = mockk<HttpRequest<*>>()
        every { request.path } returns "/api/v1/flow/sign-in"
        every { request.method } returns HttpMethod.POST
        every { request.headers.authorization } returns null

        val result = fetcher.fetchAuthentication(request).asFlow().toList()

        assertEquals(1, result.size)
        assertNull((result[0] as StateAuthentication).state)
    }

    @Test
    fun `Request to non-flow path publishes nothing`() = runTest {
        val request = mockk<HttpRequest<*>>()
        every { request.path } returns "/api/v1/other/endpoint"

        val result = fetcher.fetchAuthentication(request).asFlow().toList()

        assertEquals(0, result.size)
    }
}

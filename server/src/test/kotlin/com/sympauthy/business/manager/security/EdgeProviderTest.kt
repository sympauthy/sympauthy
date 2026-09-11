package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * The edges that read one header and publish no location, held together because the only thing that
 * separates them is which header they name — which is the whole of the case there is to make about
 * any of them.
 *
 * The ones that parse are a class apiece.
 */
class EdgeProviderTest {

    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `read - Answer the address the edge's header carries`(provider: EdgeProvider, header: String) {
        val observed = provider.read(headersOf(header to "203.0.113.7"))

        assertEquals("203.0.113.7", observed.ipAddress)
        assertNull(observed.geo)
    }

    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `read - Answer nothing where the edge's header did not arrive`(provider: EdgeProvider, header: String) {
        val observed = provider.read(headersOf("X-Something-Else" to "203.0.113.7"))

        assertNull(observed.ipAddress)
        assertNull(observed.geo)
    }

    /**
     * The value is empty rather than whitespace because a header bag refuses to hold one that is
     * only whitespace, which is the framework keeping the promise HTTP makes about a field value.
     */
    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `read - Answer nothing where the edge's header arrived empty`(provider: EdgeProvider, header: String) {
        val observed = provider.read(headersOf(header to ""))

        assertNull(observed.ipAddress)
    }

    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `read - Read the header whatever it was capitalised as`(provider: EdgeProvider, header: String) {
        val observed = provider.read(headersOf(header.lowercase() to "203.0.113.7"))

        assertEquals("203.0.113.7", observed.ipAddress)
    }

    @Test
    fun `read - Answer the same address for nginx and for traefik`() {
        val headers = headersOf("X-Real-IP" to "203.0.113.7")

        assertEquals(
            NginxEdgeProvider().read(headers),
            TraefikEdgeProvider().read(headers)
        )
    }

    companion object {

        @JvmStatic
        fun headerReadingProviders(): List<Arguments> = listOf(
            Arguments.of(NginxEdgeProvider(), "X-Real-IP"),
            Arguments.of(TraefikEdgeProvider(), "X-Real-IP"),
            Arguments.of(FastlyEdgeProvider(), "Fastly-Client-IP"),
            Arguments.of(AzureEdgeProvider(), "X-Azure-ClientIP")
        )
    }
}

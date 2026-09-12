package com.sympauthy.business.manager.security

import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.business.model.security.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * The edges that read the address out of one header, held together because the only thing that
 * separates them is which header they name — which is the whole of the case there is to make about
 * any of them.
 *
 * The ones that parse are a class apiece.
 */
class IpProviderTest {

    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `readIpOrNull - Answer the address the edge's header carries`(provider: IpProvider, header: String) {
        assertEquals("203.0.113.7", provider.readIpOrNull(headersOf(header to "203.0.113.7")))
    }

    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `readIpOrNull - Answer nothing where the edge's header did not arrive`(
        provider: IpProvider,
        header: String
    ) {
        assertNull(provider.readIpOrNull(headersOf("${header}-Something-Else" to "203.0.113.7")))
    }

    /**
     * The value is empty rather than whitespace because a header bag refuses to hold one that is
     * only whitespace, which is the framework keeping the promise HTTP makes about a field value.
     */
    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `readIpOrNull - Answer nothing where the edge's header arrived empty`(
        provider: IpProvider,
        header: String
    ) {
        assertNull(provider.readIpOrNull(headersOf(header to "")))
    }

    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `readIpOrNull - Read the header whatever it was capitalised as`(provider: IpProvider, header: String) {
        assertEquals("203.0.113.7", provider.readIpOrNull(headersOf(header.lowercase() to "203.0.113.7")))
    }

    /**
     * The caller sent the header first and the proxy appended rather than replaced, so the value the
     * edge wrote is the last one and everything before it is the caller's.
     */
    @ParameterizedTest
    @MethodSource("headerReadingProviders")
    fun `readIpOrNull - Answer the value the edge appended rather than the one the caller sent`(
        provider: IpProvider,
        header: String
    ) {
        val headers = headersOf(header to "192.0.2.66", header to "203.0.113.7")

        assertEquals("203.0.113.7", provider.readIpOrNull(headers))
    }

    @Test
    fun `readIpOrNull - Answer the same address for nginx and for traefik`() {
        val headers = headersOf("X-Real-IP" to "203.0.113.7")

        assertEquals(NginxEdge().readIpOrNull(headers), TraefikEdge().readIpOrNull(headers))
    }

    companion object {

        @JvmStatic
        fun headerReadingProviders(): List<Arguments> = listOf(
            Arguments.of(NginxEdge(), "X-Real-IP"),
            Arguments.of(TraefikEdge(), "X-Real-IP"),
            Arguments.of(FastlyEdge(), "Fastly-Client-IP"),
            Arguments.of(AzureEdge(), "X-Azure-ClientIP"),
            Arguments.of(AkamaiEdge(), "True-Client-IP")
        )
    }
}

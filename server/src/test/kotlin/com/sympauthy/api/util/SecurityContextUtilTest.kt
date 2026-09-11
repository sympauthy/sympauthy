package com.sympauthy.api.util

import com.sympauthy.business.manager.security.CloudflareEdgeProvider
import com.sympauthy.business.manager.security.GcpEdgeProvider
import com.sympauthy.business.manager.security.NginxEdgeProvider
import com.sympauthy.business.manager.security.TraefikEdgeProvider
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.business.model.security.headersOf
import com.sympauthy.config.model.ConfiguredImplementation
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.SecurityContextHeadersConfig
import com.sympauthy.config.model.advancedConfigOf
import com.sympauthy.config.model.noNamedHeaders
import com.sympauthy.config.model.trustlessSecurityContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.InetSocketAddress

/**
 * The trust model, end to end over a request nothing had to have sent.
 *
 * The providers are the shipped ones rather than doubles: what is under test is which of them is
 * believed and in what order, and a double answering a fabricated field would prove that against
 * nothing.
 */
@ExtendWith(MockKExtension::class)
class SecurityContextUtilTest {

    @Test
    fun `observe - Attribute the socket peer where the deployment named no proxy`() {
        val util = utilOf(trustlessSecurityContext())

        val observed = util.observe(requestFromPeer(headersOf(CONNECTING_IP to FORGED_IP, REAL_IP to FORGED_IP)))

        assertEquals(SOCKET_PEER, observed.ipAddress)
        assertNull(observed.geo)
    }

    @Test
    fun `observe - Believe the header of the proxy the deployment named`() {
        val util = utilOf(securityContextOf(providers = listOf("cloudflare")))

        val observed = util.observe(requestOf(headersOf(CONNECTING_IP to CALLER_IP, "CF-IPCountry" to "US")))

        assertEquals(CALLER_IP, observed.ipAddress)
        assertEquals("US", observed.geo?.countryCode)
    }

    @Test
    fun `observe - Attribute the socket peer where the named proxy's header did not arrive`() {
        val util = utilOf(securityContextOf(providers = listOf("nginx")))

        val observed = util.observe(requestFromPeer(headersOf(CONNECTING_IP to FORGED_IP)))

        assertEquals(SOCKET_PEER, observed.ipAddress)
    }

    @Test
    fun `observe - Take each field from the last proxy that answered it`() {
        val util = utilOf(securityContextOf(providers = listOf("gcp", "nginx")))

        val observed = util.observe(
            requestOf(
                headersOf(
                    "X-Forwarded-For" to "$CALLER_IP, 35.191.0.1",
                    "X-Client-Geo-Location" to "US,Mountain View",
                    REAL_IP to INGRESS_SEEN_IP
                )
            )
        )

        assertEquals(INGRESS_SEEN_IP, observed.ipAddress)
        assertEquals("US", observed.geo?.countryCode)
        assertEquals("Mountain View", observed.geo?.city)
    }

    @Test
    fun `observe - Leave a field where a later proxy answered nothing`() {
        val util = utilOf(securityContextOf(providers = listOf("cloudflare", "nginx")))

        val observed = util.observe(
            requestOf(headersOf(CONNECTING_IP to CALLER_IP, "CF-IPCountry" to "US", REAL_IP to INGRESS_SEEN_IP))
        )

        assertEquals(INGRESS_SEEN_IP, observed.ipAddress)
        assertEquals("US", observed.geo?.countryCode)
    }

    @Test
    fun `observe - Believe the header the deployment named for a field over every proxy`() {
        val util = utilOf(
            securityContextOf(
                providers = listOf("cloudflare"),
                headers = noNamedHeaders().copy(city = "X-My-Proxy-City")
            )
        )

        val observed = util.observe(
            requestOf(
                headersOf(
                    CONNECTING_IP to CALLER_IP,
                    "cf-ipcity" to "Mountain View",
                    "X-My-Proxy-City" to "Amsterdam"
                )
            )
        )

        assertEquals("Amsterdam", observed.geo?.city)
        assertEquals(CALLER_IP, observed.ipAddress)
    }

    @Test
    fun `observe - Read a named header as it stands rather than parsing it`() {
        val util = utilOf(securityContextOf(headers = noNamedHeaders().copy(clientIp = "X-Forwarded-For")))

        val observed = util.observe(requestOf(headersOf("X-Forwarded-For" to "$FORGED_IP, $CALLER_IP")))

        assertEquals("$FORGED_IP, $CALLER_IP", observed.ipAddress)
    }

    @Test
    fun `observe - Attribute the socket peer where a named header did not arrive`() {
        val util = utilOf(securityContextOf(headers = noNamedHeaders().copy(clientIp = "X-My-Proxy-Ip")))

        val observed = util.observe(requestFromPeer(headersOf(REAL_IP to FORGED_IP)))

        assertEquals(SOCKET_PEER, observed.ipAddress)
    }

    @Test
    fun `observe - Believe the edge whose headers arrived when auto-detecting`() {
        val util = utilOf(securityContextOf(autoDetect = true))

        val observed = util.observe(requestOf(headersOf(CONNECTING_IP to CALLER_IP, "CF-IPCountry" to "US")))

        assertEquals(CALLER_IP, observed.ipAddress)
        assertEquals("US", observed.geo?.countryCode)
    }

    @Test
    fun `observe - Believe the later name where two edges answered while auto-detecting`() {
        val util = utilOf(securityContextOf(autoDetect = true))

        val observed = util.observe(requestOf(headersOf(CONNECTING_IP to CALLER_IP, REAL_IP to INGRESS_SEEN_IP)))

        assertEquals(INGRESS_SEEN_IP, observed.ipAddress)
    }

    @Test
    fun `observe - Believe a proxy the deployment named over the ones auto-detection found`() {
        val util = utilOf(securityContextOf(autoDetect = true, providers = listOf("cloudflare")))

        val observed = util.observe(requestOf(headersOf(CONNECTING_IP to CALLER_IP, REAL_IP to INGRESS_SEEN_IP)))

        assertEquals(CALLER_IP, observed.ipAddress)
    }

    @Test
    fun `observe - Attribute the socket peer where auto-detection found no edge at all`() {
        val util = utilOf(securityContextOf(autoDetect = true))

        val observed = util.observe(requestFromPeer(headersOf("X-Something-Else" to FORGED_IP)))

        assertEquals(SOCKET_PEER, observed.ipAddress)
        assertNull(observed.geo)
    }

    @Test
    fun `observe - Read the user agent the request carried`() {
        val util = utilOf(trustlessSecurityContext())

        val observed = util.observe(requestFromPeer(headersOf(HttpHeaders.USER_AGENT to "Mozilla/5.0")))

        assertEquals("Mozilla/5.0", observed.userAgent)
    }

    @Test
    fun `observe - Answer no user agent where the request carried none`() {
        val util = utilOf(trustlessSecurityContext())

        val observed = util.observe(requestFromPeer(headersOf()))

        assertNull(observed.userAgent)
    }

    private fun utilOf(securityContext: SecurityContextConfig) = SecurityContextUtil(
        advancedConfigOf(securityContext = securityContext),
        edgeProviders
    )

    private fun securityContextOf(
        autoDetect: Boolean = false,
        providers: List<String> = emptyList(),
        headers: SecurityContextHeadersConfig = noNamedHeaders()
    ) = SecurityContextConfig(
        autoDetect = autoDetect,
        providers = providers.map { ConfiguredImplementation(EdgeProvider::class, it) },
        headers = headers
    )

    /**
     * A request whose socket peer is left unstubbed, so that a case about a header being believed
     * fails rather than passes if the address falls back to the peer instead.
     */
    private fun requestOf(headers: HttpHeaders): HttpRequest<*> = mockk {
        every { this@mockk.headers } returns headers
    }

    private fun requestFromPeer(headers: HttpHeaders): HttpRequest<*> = mockk {
        every { this@mockk.headers } returns headers
        every { remoteAddress } returns InetSocketAddress(SOCKET_PEER, 443)
    }

    /**
     * A set this test owns rather than everything the container publishes, so that a case about
     * auto-detection names the edges it is about and a provider added later does not move it.
     */
    private val edgeProviders = mapOf(
        "cloudflare" to CloudflareEdgeProvider(),
        "gcp" to GcpEdgeProvider(),
        "nginx" to NginxEdgeProvider(),
        "traefik" to TraefikEdgeProvider()
    )

    private companion object {

        const val CONNECTING_IP = "CF-Connecting-IP"
        const val REAL_IP = "X-Real-IP"

        /**
         * The proxy this server accepted the connection from, which is what a deployment naming no
         * proxy records for every request.
         */
        const val SOCKET_PEER = "198.51.100.1"

        /**
         * What an edge in front says the caller's address is.
         */
        const val CALLER_IP = "203.0.113.7"

        /**
         * The same caller as seen by an ingress that is one hop nearer, so that a case about which
         * proxy won names two addresses that could not be confused.
         */
        const val INGRESS_SEEN_IP = "203.0.113.99"

        /**
         * An address a caller put in a header nobody asked them for.
         */
        const val FORGED_IP = "192.0.2.66"
    }
}

package com.sympauthy.api.util

import com.sympauthy.business.manager.security.AkamaiEdge
import com.sympauthy.business.manager.security.CloudflareEdge
import com.sympauthy.business.manager.security.GcpEdge
import com.sympauthy.business.manager.security.NginxEdge
import com.sympauthy.business.model.security.GeoProvider
import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.business.model.security.CALLER_IP
import com.sympauthy.business.model.security.FORGED_IP
import com.sympauthy.business.model.security.SOCKET_PEER
import com.sympauthy.business.model.security.headersOf
import com.sympauthy.business.model.security.requestFromPeer
import com.sympauthy.business.model.security.requestOf
import com.sympauthy.config.model.ConfiguredImplementation
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.SecurityContextGeoConfig
import com.sympauthy.config.model.SecurityContextGeoHeadersConfig
import com.sympauthy.config.model.SecurityContextIpConfig
import com.sympauthy.config.model.advancedConfigOf
import com.sympauthy.config.model.noNamedGeoHeaders
import com.sympauthy.config.model.trustlessSecurityContext
import io.micronaut.http.HttpHeaders
import io.mockk.junit5.MockKExtension
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The trust model, end to end over a request nothing had to have sent.
 *
 * The edges are the shipped ones rather than doubles: what is under test is which of them is
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
        val util = utilOf(securityContextOf(ipProvider = "cloudflare"))

        val observed = util.observe(requestOf(headersOf(CONNECTING_IP to CALLER_IP)))

        assertEquals(CALLER_IP, observed.ipAddress)
    }

    @Test
    fun `observe - Attribute the socket peer where the named proxy's header did not arrive`() {
        val util = utilOf(securityContextOf(ipProvider = "nginx"))

        val observed = util.observe(requestFromPeer(headersOf(CONNECTING_IP to FORGED_IP)))

        assertEquals(SOCKET_PEER, observed.ipAddress)
    }

    @Test
    fun `observe - Take the address from the named proxy and the location from the named edges`() {
        val util = utilOf(securityContextOf(ipProvider = "nginx", geoProviders = listOf("gcp")))

        val observed = util.observe(
            requestOf(
                headersOf(
                    "X-Forwarded-For" to "$FORGED_IP, 35.191.0.1",
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
    fun `observe - Take each location field from the last edge that answered it`() {
        val util = utilOf(securityContextOf(geoProviders = listOf("gcp", "cloudflare")))

        val observed = util.observe(
            requestFromPeer(
                headersOf("X-Client-Geo-Location" to "FR,Lyon", "CF-IPCountry" to "US", "cf-timezone" to "UTC")
            )
        )

        assertEquals("US", observed.geo?.countryCode)
        assertEquals("UTC", observed.geo?.timeZone)
        assertEquals("Lyon", observed.geo?.city)
    }

    @Test
    fun `observe - Leave a location field where a later edge answered nothing`() {
        val util = utilOf(securityContextOf(geoProviders = listOf("cloudflare", "akamai")))

        val observed = util.observe(
            requestFromPeer(headersOf("cf-ipcity" to "Lyon", "X-Akamai-Edgescape" to "country_code=US"))
        )

        assertEquals("Lyon", observed.geo?.city)
        assertEquals("US", observed.geo?.countryCode)
    }

    @Test
    fun `observe - Believe the header the deployment named for a location field over every edge`() {
        val util = utilOf(
            securityContextOf(
                geoProviders = listOf("cloudflare"),
                geoHeaders = noNamedGeoHeaders().copy(city = "X-My-Proxy-City")
            )
        )

        val observed = util.observe(
            requestFromPeer(headersOf("cf-ipcity" to "Mountain View", "X-My-Proxy-City" to "Amsterdam"))
        )

        assertEquals("Amsterdam", observed.geo?.city)
    }

    @Test
    fun `observe - Believe the header the deployment named for the address over the proxy it named`() {
        val util = utilOf(securityContextOf(ipProvider = "cloudflare", ipHeader = "X-My-Proxy-Ip"))

        val headers = headersOf(CONNECTING_IP to FORGED_IP, "X-My-Proxy-Ip" to CALLER_IP)

        val observed = util.observe(requestOf(headers))

        assertEquals(CALLER_IP, observed.ipAddress)
    }

    @Test
    fun `observe - Read a named header as it stands rather than parsing it`() {
        val util = utilOf(securityContextOf(ipHeader = "X-Forwarded-For"))

        val observed = util.observe(requestOf(headersOf("X-Forwarded-For" to "$FORGED_IP, $CALLER_IP")))

        assertEquals("$FORGED_IP, $CALLER_IP", observed.ipAddress)
    }

    @Test
    fun `observe - Attribute the socket peer where a named header did not arrive`() {
        val util = utilOf(securityContextOf(ipProvider = "cloudflare", ipHeader = "X-My-Proxy-Ip"))

        val observed = util.observe(requestFromPeer(headersOf(REAL_IP to FORGED_IP)))

        assertEquals(SOCKET_PEER, observed.ipAddress)
    }

    @Test
    fun `observe - Read the location of the edge whose headers arrived when auto-detecting`() {
        val util = utilOf(securityContextOf(autoDetect = true))

        val observed = util.observe(requestFromPeer(headersOf("CF-IPCountry" to "US")))

        assertEquals("US", observed.geo?.countryCode)
    }

    @Test
    fun `observe - Attribute the address to the socket peer while auto-detecting a location`() {
        val util = utilOf(securityContextOf(autoDetect = true))

        val observed = util.observe(
            requestFromPeer(headersOf(CONNECTING_IP to FORGED_IP, "X-Forwarded-For" to "$FORGED_IP, 35.191.0.1"))
        )

        assertEquals(SOCKET_PEER, observed.ipAddress)
    }

    @Test
    fun `observe - Answer no location where auto-detection found no edge at all`() {
        val util = utilOf(securityContextOf(autoDetect = true))

        val observed = util.observe(requestFromPeer(headersOf("X-Something-Else" to FORGED_IP)))

        assertNull(observed.geo)
    }

    /**
     * Naming an edge that auto-detection already found moves it to the end, where it wins the fields
     * it answers. It is read once all the same: the stub would answer the same thing twice, so only
     * the verification shows the second read is not made.
     */
    @Test
    fun `observe - Read an edge auto-detection already found once, at the position it was named`() {
        val counted = spyk(CloudflareEdge())
        val util = SecurityContextUtil(
            advancedConfigOf(securityContext = securityContextOf(autoDetect = true, geoProviders = listOf("cf"))),
            ipProviders,
            mapOf("akamai" to AkamaiEdge(), "cf" to counted)
        )
        val headers = headersOf("CF-IPCountry" to "US", "X-Akamai-Edgescape" to "country_code=FR")

        val observed = util.observe(requestFromPeer(headers))

        assertEquals("US", observed.geo?.countryCode)
        verify(exactly = 1) { counted.readGeoOrNull(headers) }
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
        ipProviders,
        geoProviders
    )

    private fun securityContextOf(
        ipProvider: String? = null,
        ipHeader: String? = null,
        autoDetect: Boolean = false,
        geoProviders: List<String> = emptyList(),
        geoHeaders: SecurityContextGeoHeadersConfig = noNamedGeoHeaders()
    ) = SecurityContextConfig(
        ip = SecurityContextIpConfig(
            provider = ipProvider?.let { ConfiguredImplementation(IpProvider::class, it) },
            header = ipHeader
        ),
        geo = SecurityContextGeoConfig(
            autoDetect = autoDetect,
            providers = geoProviders.map { ConfiguredImplementation(GeoProvider::class, it) },
            headers = geoHeaders
        )
    )

    /**
     * Sets this test owns rather than everything the container publishes, so that a case about
     * auto-detection names the edges it is about and an edge added later does not move it.
     */
    private val ipProviders = mapOf<String, IpProvider>(
        "cloudflare" to CloudflareEdge(),
        "gcp" to GcpEdge(),
        "nginx" to NginxEdge()
    )

    private val geoProviders = mapOf<String, GeoProvider>(
        "akamai" to AkamaiEdge(),
        "cloudflare" to CloudflareEdge(),
        "gcp" to GcpEdge()
    )

    private companion object {

        const val CONNECTING_IP = "CF-Connecting-IP"
        const val REAL_IP = "X-Real-IP"

        /**
         * The caller as seen by an ingress one hop nearer than the edge in front of it, so that a
         * case about which proxy won names two addresses that could not be confused.
         */
        const val INGRESS_SEEN_IP = "203.0.113.99"
    }
}

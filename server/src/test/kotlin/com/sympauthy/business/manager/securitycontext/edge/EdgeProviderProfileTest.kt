package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.business.model.securitycontext.observedRequestOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class EdgeProviderProfileTest {

    @Test
    fun `clientIp - Read no header where the deployment named no proxy`() {
        val request = observedRequestOf(
            peer = "198.51.100.10",
            "CF-Connecting-IP" to "1.2.3.4",
            "X-Forwarded-For" to "1.2.3.4",
            "X-Real-IP" to "1.2.3.4"
        )

        assertEquals("198.51.100.10", NoneEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Read X-Real-IP under nginx`() {
        val request = observedRequestOf(peer = "10.0.0.1", "X-Real-IP" to "198.51.100.10")

        assertEquals("198.51.100.10", NginxEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Read X-Real-IP under Traefik`() {
        val request = observedRequestOf(peer = "10.0.0.1", "X-Real-IP" to "198.51.100.10")

        assertEquals("198.51.100.10", TraefikEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Answer null where the header the profile reads did not arrive`() {
        val request = observedRequestOf(peer = "10.0.0.1")

        assertNull(NginxEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Take the last entry of X-Forwarded-For under Caddy`() {
        val request = observedRequestOf(peer = "10.0.0.1", "X-Forwarded-For" to "203.0.113.9, 198.51.100.10")

        assertEquals("198.51.100.10", CaddyEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Take the entry before the load balancer under GCP`() {
        val request = observedRequestOf(
            peer = "10.0.0.1",
            "X-Forwarded-For" to "203.0.113.9, 198.51.100.10, 34.117.0.1"
        )

        assertEquals("198.51.100.10", GcpEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Take the entry from the right across repeated X-Forwarded-For lines`() {
        val request = ObservedRequest(
            peer = "10.0.0.1",
            headers = mapOf("X-Forwarded-For" to listOf("203.0.113.9", "198.51.100.10, 34.117.0.1"))
        )

        assertEquals("198.51.100.10", GcpEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Read CF-Connecting-IP under Cloudflare`() {
        val request = observedRequestOf(peer = "10.0.0.1", "CF-Connecting-IP" to "198.51.100.10")

        assertEquals("198.51.100.10", CloudflareEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Strip the source port off the viewer address under CloudFront`() {
        val request = observedRequestOf(peer = "10.0.0.1", "CloudFront-Viewer-Address" to "198.51.100.10:46532")

        assertEquals("198.51.100.10", CloudFrontEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Strip the source port off an IPv6 viewer address under CloudFront`() {
        val request = observedRequestOf(peer = "10.0.0.1", "CloudFront-Viewer-Address" to "2001:db8::1:46532")

        assertEquals("2001:db8::1", CloudFrontEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Read True-Client-IP under Akamai`() {
        val request = observedRequestOf(peer = "10.0.0.1", "True-Client-IP" to "198.51.100.10")

        assertEquals("198.51.100.10", AkamaiEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Read Fastly-Client-IP under Fastly`() {
        val request = observedRequestOf(peer = "10.0.0.1", "Fastly-Client-IP" to "198.51.100.10")

        assertEquals("198.51.100.10", FastlyEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Read X-Azure-ClientIP under Azure`() {
        val request = observedRequestOf(
            peer = "10.0.0.1",
            "X-Azure-ClientIP" to "198.51.100.10",
            "X-Azure-SocketIP" to "203.0.113.9"
        )

        assertEquals("198.51.100.10", AzureEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `clientIp - Fall back to X-Azure-SocketIP where Front Door sent no client address`() {
        val request = observedRequestOf(peer = "10.0.0.1", "X-Azure-SocketIP" to "203.0.113.9")

        assertEquals("203.0.113.9", AzureEdgeProviderProfile().clientIp(request))
    }

    @Test
    fun `read - Read the visitor location headers under Cloudflare`() {
        val request = observedRequestOf(
            peer = "10.0.0.1",
            "CF-IPCountry" to "FR",
            "cf-region" to "Occitanie",
            "cf-region-code" to "OCC",
            "cf-ipcity" to "Toulouse"
        )

        val profile = CloudflareEdgeProviderProfile()

        assertEquals("FR", profile.country(request))
        assertEquals("OCC", profile.region(request))
        assertEquals("Toulouse", profile.city(request))
    }

    @Test
    fun `read - Read the viewer location headers under CloudFront`() {
        val request = observedRequestOf(
            peer = "10.0.0.1",
            "CloudFront-Viewer-Country" to "US",
            "CloudFront-Viewer-Country-Region" to "MA",
            "CloudFront-Viewer-Country-Region-Name" to "Massachusetts",
            "CloudFront-Viewer-City" to "Cambridge"
        )

        val profile = CloudFrontEdgeProviderProfile()

        assertEquals("US", profile.country(request))
        assertEquals("MA", profile.region(request))
        assertEquals("Cambridge", profile.city(request))
    }

    @Test
    fun `read - Split the geo location header into a country and a city under GCP`() {
        val request = observedRequestOf(peer = "10.0.0.1", "X-Client-Geo-Location" to "US,Mountain View")

        val profile = GcpEdgeProviderProfile()

        assertEquals("US", profile.country(request))
        assertEquals("Mountain View", profile.city(request))
        assertNull(profile.region(request))
    }

    @Test
    fun `read - Scan the edgescape header under Akamai`() {
        val request = observedRequestOf(
            peer = "10.0.0.1",
            "X-Akamai-Edgescape" to "georegion=263,country_code=US,region_code=MA,city=CAMBRIDGE,lat=42.3933"
        )

        val profile = AkamaiEdgeProviderProfile()

        assertEquals("US", profile.country(request))
        assertEquals("MA", profile.region(request))
        assertEquals("CAMBRIDGE", profile.city(request))
    }

    @Test
    fun `read - Answer null for a field the edgescape header left empty`() {
        val request = observedRequestOf(
            peer = "10.0.0.1",
            "X-Akamai-Edgescape" to "country_code=US,region_code=,city="
        )

        val profile = AkamaiEdgeProviderProfile()

        assertEquals("US", profile.country(request))
        assertNull(profile.region(request))
        assertNull(profile.city(request))
    }

    @Test
    fun `read - Answer null for every geo field where the profile's headers did not arrive`() {
        val request = observedRequestOf(peer = "10.0.0.1", "CF-Connecting-IP" to "198.51.100.10")

        val profile = CloudflareEdgeProviderProfile()

        assertNull(profile.country(request))
        assertNull(profile.region(request))
        assertNull(profile.city(request))
    }

    @ParameterizedTest
    @MethodSource("profilesPublishingNoGeo")
    fun `read - Answer null for the geo a provider publishes under no name of its own`(
        profile: EdgeProviderProfile
    ) {
        val request = observedRequestOf(
            peer = "10.0.0.1",
            "CF-IPCountry" to "FR",
            "CloudFront-Viewer-Country" to "US",
            "X-Client-Geo-Location" to "US,Mountain View",
            "X-Akamai-Edgescape" to "country_code=US,region_code=MA,city=CAMBRIDGE"
        )

        assertNull(profile.country(request))
        assertNull(profile.region(request))
        assertNull(profile.city(request))
    }

    companion object {

        @JvmStatic
        fun profilesPublishingNoGeo() = listOf(
            NoneEdgeProviderProfile(),
            NginxEdgeProviderProfile(),
            TraefikEdgeProviderProfile(),
            CaddyEdgeProviderProfile(),
            FastlyEdgeProviderProfile(),
            AzureEdgeProviderProfile()
        ).map { Named.of(it.name, it) }
    }
}

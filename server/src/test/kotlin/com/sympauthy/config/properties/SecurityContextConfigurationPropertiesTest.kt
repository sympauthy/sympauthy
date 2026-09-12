package com.sympauthy.config.properties

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * What a deployment writes under `advanced.security-context` reaching the properties the parser
 * reads.
 *
 * The parser's own tests hand it a properties object built in Kotlin, which answers what it does
 * with the values and nothing about whether a file produces them. The list is the half worth
 * proving: it is the only key of that shape in this domain, and a spelling the container does not
 * bind leaves the deployment with no edge named and no error saying so. An indexed key is the form
 * a YAML list is flattened into, so it is the form worth binding.
 *
 * One context carries every value, rather than one per case. A context built and closed while the
 * suite is running leaves background work behind that throws into whichever coroutine test the
 * runner schedules next, so the properties are set on the class and the framework closes the
 * context once, at the end.
 */
@MicronautTest(environments = ["test", "h2"])
@Property(name = "advanced.security-context.ip.provider", value = "nginx")
@Property(name = "advanced.security-context.geo.providers[0]", value = "gcp")
@Property(name = "advanced.security-context.geo.providers[1]", value = "cloudflare")
@Property(name = "advanced.security-context.geo.headers.city", value = "X-My-Proxy-City")
class SecurityContextConfigurationPropertiesTest {

    @Inject
    lateinit var ip: SecurityContextIpConfigurationProperties

    @Inject
    lateinit var geo: SecurityContextGeoConfigurationProperties

    @Inject
    lateinit var geoHeaders: SecurityContextGeoHeadersConfigurationProperties

    @Test
    fun `Bind the geo providers a file lists, in the order it wrote them`() {
        assertEquals(listOf("gcp", "cloudflare"), geo.providers)
    }

    @Test
    fun `Bind the one proxy a file names for the address`() {
        assertEquals("nginx", ip.provider)
    }

    @Test
    fun `Bind the header a file named for one location field`() {
        assertEquals("X-My-Proxy-City", geoHeaders.city)
    }

    @Test
    fun `Leave unbound every key the file did not write`() {
        assertNull(ip.header)
        assertNull(geo.autoDetect)
        assertNull(geoHeaders.countryCode)
        assertNull(geoHeaders.timeZone)
    }
}

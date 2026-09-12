package com.sympauthy.config

import com.sympauthy.api.util.SecurityContextUtil
import com.sympauthy.business.model.security.GeoProvider
import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.business.model.security.CALLER_IP
import com.sympauthy.business.model.security.FORGED_IP
import com.sympauthy.business.model.security.SOCKET_PEER
import com.sympauthy.business.model.security.headersOf
import com.sympauthy.business.model.security.requestFromPeer
import com.sympauthy.business.model.security.requestOf
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.ConfiguredImplementation
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.SecurityContextGeoConfig
import com.sympauthy.config.model.SecurityContextIpConfig
import com.sympauthy.config.model.advancedConfigOf
import com.sympauthy.config.model.noNamedGeoHeaders
import com.sympauthy.config.model.orThrow
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.junit5.MockKExtension
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The word a deployment writes reaching the edge that reads its requests.
 *
 * Everything on either side of that is proved somewhere already — that a file binds to the
 * properties, that the parser refuses a word naming no implementation, that an edge reads its own
 * header — and none of it says that the word an operator wrote selects the bean the container
 * published under it. That is what this holds, against the container's own maps rather than ones a
 * test assembled.
 *
 * It also holds the two sets apart: an edge publishing no location is absent from the geo map, which
 * is what makes naming one under the geo setting a refusal rather than a setting with no effect.
 */
@MicronautTest(environments = ["default", "test", "h2"])
@ExtendWith(MockKExtension::class)
class SecurityContextWiringTest {

    @Inject
    lateinit var ipProviders: Map<String, IpProvider>

    @Inject
    lateinit var geoProviders: Map<String, GeoProvider>

    /**
     * The utility as the container built it, over the configuration the server ships.
     */
    @Inject
    lateinit var util: SecurityContextUtil

    @Inject
    lateinit var advancedConfig: AdvancedConfig

    @Inject
    lateinit var reader: PublishedImplementationReader

    @Test
    fun `The shipped configuration attributes a forged header to the socket peer`() {
        val observed = util.observe(requestFromPeer(headersOf(CONNECTING_IP to FORGED_IP)))

        assertEquals(SOCKET_PEER, observed.ipAddress)
        assertNull(observed.geo)
    }

    @Test
    fun `The shipped configuration names no proxy and does not auto-detect`() {
        val securityContext = advancedConfig.orThrow().securityContext

        assertNull(securityContext.ip.provider)
        assertNull(securityContext.ip.header)
        assertEquals(emptyList<String>(), securityContext.geo.providers.map { it.qualifier })
        assertFalse(securityContext.geo.autoDetect)
    }

    @Test
    fun `A deployment naming cloudflare attributes the same request to the header`() {
        val observed = utilNaming("cloudflare").observe(requestOf(headersOf(CONNECTING_IP to FORGED_IP)))

        assertEquals(FORGED_IP, observed.ipAddress)
    }

    @Test
    fun `A deployment naming an edge selects the bean published under that word`() {
        val headers = headersOf(CONNECTING_IP to FORGED_IP, "True-Client-IP" to CALLER_IP)

        assertEquals(CALLER_IP, utilNaming("akamai").observe(requestOf(headers)).ipAddress)
    }

    @Test
    fun `Every edge publishes an address, and only the ones with a location publish that`() {
        assertEquals(
            setOf("akamai", "azure", "caddy", "cloudflare", "cloudfront", "fastly", "gcp", "nginx", "traefik"),
            ipProviders.keys
        )
        assertEquals(setOf("akamai", "cloudflare", "cloudfront", "gcp"), geoProviders.keys)
    }

    /**
     * The set a deployment may name under the geo setting is the one the container publishes, so an
     * edge with no location to give is refused there rather than accepted to no effect.
     */
    @Test
    fun `An edge publishing no location is not a word the geo setting accepts`() {
        assertFalse("nginx" in reader.read(GeoProvider::class).qualifiers)
        assertTrue("nginx" in reader.read(IpProvider::class).qualifiers)
    }

    private fun utilNaming(qualifier: String) = SecurityContextUtil(
        advancedConfigOf(
            securityContext = SecurityContextConfig(
                ip = SecurityContextIpConfig(ConfiguredImplementation(IpProvider::class, qualifier), null),
                geo = SecurityContextGeoConfig(false, emptyList(), noNamedGeoHeaders()),
                knownUserRetention = Duration.ofDays(180)
            )
        ),
        ipProviders,
        geoProviders
    )

    private companion object {

        const val CONNECTING_IP = "CF-Connecting-IP"
    }
}

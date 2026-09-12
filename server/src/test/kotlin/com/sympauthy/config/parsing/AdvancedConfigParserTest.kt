package com.sympauthy.config.parsing

import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.security.GeoProvider
import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.PublishedImplementations
import com.sympauthy.config.model.ConfiguredImplementation
import com.sympauthy.config.properties.AdvancedConfigurationProperties
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties
import com.sympauthy.config.properties.HashConfigurationProperties
import com.sympauthy.config.properties.InvitationConfigurationProperties
import com.sympauthy.config.properties.InvitationHashConfigurationProperties
import com.sympauthy.config.properties.JwtConfigurationProperties
import com.sympauthy.config.properties.PaginationConfigurationProperties
import com.sympauthy.config.properties.SecurityContextConfigurationProperties
import com.sympauthy.config.properties.SecurityContextGeoConfigurationProperties
import com.sympauthy.config.properties.SecurityContextGeoHeadersConfigurationProperties
import com.sympauthy.config.properties.SecurityContextIpConfigurationProperties
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class AdvancedConfigParserTest {

    private val parser = AdvancedConfigParser(ConfigParser())

    @Test
    fun `parse - Read the generation strategy the deployment named`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, "auto-increment")

        assertEquals(
            ConfiguredImplementation(CryptoKeysGenerationStrategy::class, "auto-increment"),
            parsed.keysGenerationStrategy
        )
        assertEquals(emptyList<Pair<String, String>>(), ctx.errors.map { it.key to it.messageId })
    }

    @Test
    fun `parse - Report a generation strategy naming no implementation`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, "autoincrement")

        assertNull(parsed.keysGenerationStrategy)
        assertEquals(
            listOf("advanced.keys-generation-strategy" to "config.unknown_implementation"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Offer the published implementations to a deployment that named none of them`() {
        val ctx = ConfigParsingContext()

        parse(ctx, "autoincrement")

        assertEquals(
            mapOf("value" to "autoincrement", "supportedValues" to "auto-increment, in-memory"),
            ctx.errors.single().values
        )
    }

    @Test
    fun `parse - Report a blank generation strategy against the published implementations`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, "  ")

        assertNull(parsed.keysGenerationStrategy)
        assertEquals(
            listOf("advanced.keys-generation-strategy" to "config.unknown_implementation"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Report a missing generation strategy`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, null)

        assertNull(parsed.keysGenerationStrategy)
        assertEquals(
            listOf("advanced.keys-generation-strategy" to "config.missing"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Read the geo providers in the order the deployment wrote them`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, geoProviders = listOf("second-edge", "first-edge"))

        assertEquals(
            listOf("second-edge", "first-edge"),
            parsed.securityContext.geo.providers.map { it.implementation.qualifier }
        )
        assertEquals(emptyList<Pair<String, String>>(), ctx.errors.map { it.key to it.messageId })
    }

    @Test
    fun `parse - Report a geo provider naming no implementation against the position it holds`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, geoProviders = listOf("first-edge", "nowhere", "elsewhere"))

        assertEquals(listOf("first-edge"), parsed.securityContext.geo.providers.map { it.implementation.qualifier })
        assertEquals(
            listOf(
                "advanced.security-context.geo.providers[1]" to "config.unknown_implementation",
                "advanced.security-context.geo.providers[2]" to "config.unknown_implementation"
            ),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Read the one proxy the deployment named for the address`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, ipProvider = "first-hop")

        assertEquals("first-hop", parsed.securityContext.ip.provider?.qualifier)
        assertEquals(emptyList<Pair<String, String>>(), ctx.errors.map { it.key to it.messageId })
    }

    @Test
    fun `parse - Report a proxy naming no implementation`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, ipProvider = "nowhere")

        assertNull(parsed.securityContext.ip.provider)
        assertEquals(
            listOf("advanced.security-context.ip.provider" to "config.unknown_implementation"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Read nothing where the deployment named neither a proxy nor an edge`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx)

        assertNull(parsed.securityContext.ip.provider)
        assertNull(parsed.securityContext.ip.header)
        assertEquals(emptyList<String>(), parsed.securityContext.geo.providers.map { it.implementation.qualifier })
        assertNull(parsed.securityContext.geo.autoDetect)
        assertEquals(emptyList<Pair<String, String>>(), ctx.errors.map { it.key to it.messageId })
    }

    @Test
    fun `parse - Read auto-detect as a boolean`() {
        val ctx = ConfigParsingContext()

        assertEquals(true, parse(ctx, autoDetect = "true").securityContext.geo.autoDetect)
    }

    @Test
    fun `parse - Report an auto-detect that is not a boolean`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, autoDetect = "sometimes")

        assertNull(parsed.securityContext.geo.autoDetect)
        assertEquals(
            listOf("advanced.security-context.geo.auto-detect" to "config.invalid_boolean"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Read the header a deployment named for one location field`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, geoHeaders = geoHeadersNaming(city = "X-My-Proxy-City"))

        assertEquals("X-My-Proxy-City", parsed.securityContext.geo.headers.city)
        assertNull(parsed.securityContext.geo.headers.countryCode)
    }

    @Test
    fun `parse - Read the header a deployment named for the address`() {
        val ctx = ConfigParsingContext()

        assertEquals("X-My-Proxy-Ip", parse(ctx, ipHeader = "X-My-Proxy-Ip").securityContext.ip.header)
    }

    @Test
    fun `parse - Read the retention a deployment wrote`() {
        val ctx = ConfigParsingContext()

        assertEquals(
            Duration.ofDays(30),
            parse(ctx, knownUserRetention = "30d").securityContext.knownUserRetention
        )
    }

    @Test
    fun `parse - Read no retention where the deployment wrote none`() {
        val ctx = ConfigParsingContext()

        assertNull(parse(ctx).securityContext.knownUserRetention)
    }

    private fun parse(
        ctx: ConfigParsingContext,
        keysGenerationStrategy: String? = "auto-increment",
        ipProvider: String? = null,
        ipHeader: String? = null,
        autoDetect: String? = null,
        geoProviders: List<String>? = null,
        geoHeaders: SecurityContextGeoHeadersConfigurationProperties = noGeoHeaders,
        knownUserRetention: String? = null
    ) = parser.parse(
        ctx = ctx,
        properties = advancedProperties(keysGenerationStrategy),
        keysGenerationStrategies = keysGenerationStrategies,
        jwtProperties = jwtProperties,
        hashProperties = hashProperties,
        invitationProperties = invitationProperties,
        invitationHashProperties = invitationHashProperties,
        validationCodeProperties = validationCodeProperties,
        authorizationWebhookProperties = authorizationWebhookProperties,
        paginationProperties = paginationProperties,
        securityContextProperties = object : SecurityContextConfigurationProperties {
            override val knownUserRetention = knownUserRetention
        },
        ipProperties = object : SecurityContextIpConfigurationProperties {
            override val provider = ipProvider
            override val header = ipHeader
        },
        geoProperties = object : SecurityContextGeoConfigurationProperties {
            override val autoDetect = autoDetect
            override val providers = geoProviders
        },
        geoHeadersProperties = geoHeaders,
        ipProviders = publishedIpProviders,
        geoProviders = publishedGeoProviders
    )

    /**
     * Sets this test owns, for the reason the generation strategies below are one.
     */
    private val publishedIpProviders = PublishedImplementations(
        IpProvider::class,
        sortedSetOf("first-hop", "second-hop")
    )

    private val publishedGeoProviders = PublishedImplementations(
        GeoProvider::class,
        sortedSetOf("first-edge", "second-edge")
    )

    private fun geoHeadersNaming(
        city: String? = null
    ) = object : SecurityContextGeoHeadersConfigurationProperties {
        override val countryCode = null
        override val regionCode = null
        override val region = null
        override val city = city
        override val postalCode = null
        override val timeZone = null
    }

    /**
     * The shipped state of the overrides: a deployment that named no header of its own.
     */
    private val noGeoHeaders = geoHeadersNaming()

    /**
     * A set this test owns rather than whichever implementations the server happens to publish, so
     * that a case about an unknown word does not become one about the shipped set.
     */
    private val keysGenerationStrategies = PublishedImplementations(
        CryptoKeysGenerationStrategy::class,
        sortedSetOf("auto-increment", "in-memory")
    )

    /**
     * The values the shipped configuration holds, so that a case names the only value it is about.
     */
    private fun advancedProperties(strategy: String?) = object : AdvancedConfigurationProperties {
        override val keysGenerationStrategy = strategy
    }

    private val jwtProperties = object : JwtConfigurationProperties {
        override val publicAlg = "es256"
        override val accessAlg = "es256"
        override val privateAlg = "hs256"
    }

    private val hashProperties = object : HashConfigurationProperties {
        override val costParameter = "16384"
        override val blockSize = "8"
        override val parallelizationParameter = "1"
        override val keyLength = "32"
        override val saltLength = "256"
    }

    private val invitationProperties = object : InvitationConfigurationProperties {
        override val tokenLength = "32"
        override val defaultExpiration = "7d"
        override val maxExpiration = "30d"
    }

    private val invitationHashProperties = object : InvitationHashConfigurationProperties {
        override val costParameter = "16384"
        override val blockSize = "8"
        override val parallelizationParameter = "1"
        override val keyLength = "32"
        override val saltLength = "256"
    }

    private val validationCodeProperties = object : ValidationCodeConfigurationProperties {
        override val expiration = "10m"
        override val length = "6"
        override val resendDelay = "1m"
    }

    private val authorizationWebhookProperties = object : AuthorizationWebhookConfigurationProperties {
        override val timeout = "5s"
    }

    private val paginationProperties = object : PaginationConfigurationProperties {
        override val defaultSize = "20"
        override val maxSize = "100"
    }
}

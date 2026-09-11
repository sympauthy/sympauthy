package com.sympauthy.config.parsing

import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.security.EdgeProvider
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
import com.sympauthy.config.properties.SecurityContextHeadersConfigurationProperties
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
    fun `parse - Read the providers in the order the deployment wrote them`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, providers = listOf("second-edge", "first-edge"))

        assertEquals(
            listOf("second-edge", "first-edge"),
            parsed.securityContext.providers.map { it.qualifier }
        )
        assertEquals(emptyList<Pair<String, String>>(), ctx.errors.map { it.key to it.messageId })
    }

    @Test
    fun `parse - Report a provider naming no implementation against the position it holds`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, providers = listOf("first-edge", "nowhere", "elsewhere"))

        assertEquals(listOf("first-edge"), parsed.securityContext.providers.map { it.qualifier })
        assertEquals(
            listOf(
                "advanced.security-context.providers[1]" to "config.unknown_implementation",
                "advanced.security-context.providers[2]" to "config.unknown_implementation"
            ),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Read no provider where the deployment named none`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx)

        assertEquals(emptyList<String>(), parsed.securityContext.providers.map { it.qualifier })
        assertNull(parsed.securityContext.autoDetect)
        assertEquals(emptyList<Pair<String, String>>(), ctx.errors.map { it.key to it.messageId })
    }

    @Test
    fun `parse - Read auto-detect as a boolean`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, autoDetect = "true")

        assertEquals(true, parsed.securityContext.autoDetect)
    }

    @Test
    fun `parse - Report an auto-detect that is not a boolean`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, autoDetect = "sometimes")

        assertNull(parsed.securityContext.autoDetect)
        assertEquals(
            listOf("advanced.security-context.auto-detect" to "config.invalid_boolean"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Read the header a deployment named for one field`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(
            ctx,
            headers = object : SecurityContextHeadersConfigurationProperties {
                override val clientIp = null
                override val countryCode = null
                override val regionCode = null
                override val region = null
                override val city = "X-My-Proxy-City"
                override val postalCode = null
                override val timeZone = null
            }
        )

        assertEquals("X-My-Proxy-City", parsed.securityContext.headers.city)
        assertNull(parsed.securityContext.headers.clientIp)
    }

    private fun parse(
        ctx: ConfigParsingContext,
        keysGenerationStrategy: String? = "auto-increment",
        autoDetect: String? = null,
        providers: List<String>? = null,
        headers: SecurityContextHeadersConfigurationProperties = noHeaders
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
        securityContextProperties = securityContextProperties(autoDetect, providers),
        securityContextHeadersProperties = headers,
        edgeProviders = edgeProviders
    )

    /**
     * A set this test owns, for the reason the generation strategies below are one.
     */
    private val edgeProviders = PublishedImplementations(
        EdgeProvider::class,
        sortedSetOf("first-edge", "second-edge")
    )

    private fun securityContextProperties(autoDetect: String?, providers: List<String>?) =
        object : SecurityContextConfigurationProperties {
            override val autoDetect = autoDetect
            override val providers = providers
        }

    /**
     * The shipped state of the overrides: a deployment that named no header of its own.
     */
    private val noHeaders = object : SecurityContextHeadersConfigurationProperties {
        override val clientIp = null
        override val countryCode = null
        override val regionCode = null
        override val region = null
        override val city = null
        override val postalCode = null
        override val timeZone = null
    }

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

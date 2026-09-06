package com.sympauthy.config.parsing

import com.sympauthy.business.model.key.CryptoKeysGenerationStrategyId
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.AdvancedConfigurationProperties
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties
import com.sympauthy.config.properties.HashConfigurationProperties
import com.sympauthy.config.properties.InvitationConfigurationProperties
import com.sympauthy.config.properties.InvitationHashConfigurationProperties
import com.sympauthy.config.properties.JwtConfigurationProperties
import com.sympauthy.config.properties.CleanupConfigurationProperties
import com.sympauthy.config.properties.PaginationConfigurationProperties
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

        assertEquals(CryptoKeysGenerationStrategyId.AUTO_INCREMENT, parsed.keysGenerationStrategyId)
        assertEquals(emptyList<Pair<String, String>>(), ctx.errors.map { it.key to it.messageId })
    }

    @Test
    fun `parse - Report a generation strategy naming no strategy`() {
        // The qualifier the implementation is published under is no longer what an operator writes.
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, "autoincrement")

        assertNull(parsed.keysGenerationStrategyId)
        assertEquals(
            listOf("advanced.keys-generation-strategy" to "config.invalid_enum_value"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    @Test
    fun `parse - Report a missing generation strategy`() {
        val ctx = ConfigParsingContext()

        val parsed = parse(ctx, null)

        assertNull(parsed.keysGenerationStrategyId)
        assertEquals(
            listOf("advanced.keys-generation-strategy" to "config.missing"),
            ctx.errors.map { it.key to it.messageId }
        )
    }

    private fun parse(ctx: ConfigParsingContext, keysGenerationStrategy: String?) = parser.parse(
        ctx = ctx,
        properties = advancedProperties(keysGenerationStrategy),
        jwtProperties = jwtProperties,
        hashProperties = hashProperties,
        invitationProperties = invitationProperties,
        invitationHashProperties = invitationHashProperties,
        validationCodeProperties = validationCodeProperties,
        authorizationWebhookProperties = authorizationWebhookProperties,
        paginationProperties = paginationProperties,
        cleanupProperties = cleanupProperties
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

    private val cleanupProperties = object : CleanupConfigurationProperties {
        override val batchSize = "1000"
    }
}

package com.sympauthy.config.validation

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategyId
import com.sympauthy.business.manager.securitycontext.edge.CloudflareEdgeProviderProfile
import com.sympauthy.business.manager.securitycontext.edge.NoneEdgeProviderProfile
import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedAccessReviewWebhookConfig
import com.sympauthy.config.parsing.ParsedAdvancedConfig
import com.sympauthy.config.parsing.ParsedHashConfig
import com.sympauthy.config.parsing.ParsedInvitationConfig
import com.sympauthy.config.parsing.ParsedPaginationConfig
import com.sympauthy.config.parsing.ParsedSecurityContextConfig
import com.sympauthy.config.parsing.ParsedValidationCodeConfig
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdvancedConfigValidatorTest {

    private val validator = AdvancedConfigValidator()

    @Test
    fun `validate - Return no configuration when a pagination bound did not parse`() {
        val ctx = ConfigParsingContext()
        ctx.addError(configExceptionOf("advanced.pagination.max-size", "config.missing"))

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(20, null)), profilesByName)

        assertNull(config)
    }

    @Test
    fun `validate - Keep the configured pagination bounds`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(50, 500)), profilesByName)

        assertNotNull(config)
        assertEquals(50, config!!.pagination.defaultSize)
        assertEquals(500, config.pagination.maxSize)
    }

    @Test
    fun `validate - Reject a default page size below one`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(0, 100)), profilesByName)

        assertNull(config)
        assertEquals(
            listOf("config.advanced.pagination.invalid_default_size"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Reject a maximum page size below one`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(20, 0)), profilesByName)

        assertNull(config)
        assertEquals(
            listOf("config.advanced.pagination.invalid_max_size"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Reject a default page size above the maximum`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(200, 100)), profilesByName)

        assertNull(config)
        assertEquals(
            listOf("config.advanced.pagination.default_exceeds_max"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Keep the configured retentions`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(
            ctx,
            parsedConfig(securityContext = parsedSecurityContext()),
            profilesByName
        )

        assertNotNull(config)
        assertEquals(Duration.ofHours(24), config!!.securityContext.unknownRetention)
        assertEquals(Duration.ofDays(180), config.securityContext.knownRetention)
    }

    @Test
    fun `validate - Keep the bounds every access-review webhook is called under`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(), profilesByName)

        assertNotNull(config)
        assertEquals(Duration.ofSeconds(2), config!!.accessReviewWebhook.timeout)
        assertEquals(10, config.accessReviewWebhook.pastContexts)
    }

    @Test
    fun `validate - Reject a negative number of past contexts`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(
            ctx,
            parsedConfig(
                accessReviewWebhook = ParsedAccessReviewWebhookConfig(
                    timeout = Duration.ofSeconds(2),
                    pastContexts = -1
                )
            ),
            profilesByName
        )

        assertNull(config)
        assertEquals(
            listOf("config.advanced.webhooks.access_review.invalid_past_contexts"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Resolve the provider to the extraction published for it`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(
            ctx,
            parsedConfig(securityContext = parsedSecurityContext(provider = "cloudflare")),
            profilesByName
        )

        assertNotNull(config)
        assertEquals("cloudflare", config!!.securityContext.profile.name)
    }

    @Test
    fun `validate - Reject a provider no extraction is published for`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(
            ctx,
            parsedConfig(securityContext = parsedSecurityContext(provider = "cloudflaire")),
            profilesByName
        )

        assertNull(config)
        assertEquals(
            listOf("config.advanced.security_context.unknown_provider"),
            ctx.errors.map { it.messageId }
        )
        assertEquals(
            mapOf("provider" to "cloudflaire", "supportedProviders" to "cloudflare, none"),
            ctx.errors.first().values
        )
    }

    @Test
    fun `validate - Reject a retention of nothing for a context no user is attached to`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(
            ctx,
            parsedConfig(securityContext = parsedSecurityContext(unknownRetention = Duration.ZERO)),
            profilesByName
        )

        assertNull(config)
        assertEquals(
            listOf("config.advanced.security_context.invalid_unknown_retention"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Reject a retention of nothing for a context attached to a user`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(
            ctx,
            parsedConfig(securityContext = parsedSecurityContext(knownRetention = Duration.ZERO)),
            profilesByName
        )

        assertNull(config)
        assertEquals(
            listOf("config.advanced.security_context.invalid_known_retention"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Reject an unknown retention outliving the known one`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(
            ctx,
            parsedConfig(
                securityContext = parsedSecurityContext(
                    unknownRetention = Duration.ofDays(200),
                    knownRetention = Duration.ofDays(180)
                )
            ),
            profilesByName
        )

        assertNull(config)
        assertEquals(
            listOf("config.advanced.security_context.unknown_exceeds_known"),
            ctx.errors.map { it.messageId }
        )
    }

    private val profilesByName: Map<String, EdgeProviderProfile> = listOf(
        NoneEdgeProviderProfile(),
        CloudflareEdgeProviderProfile()
    ).associateBy(EdgeProviderProfile::name)

    private fun parsedSecurityContext(
        provider: String? = "none",
        unknownRetention: Duration? = Duration.ofHours(24),
        knownRetention: Duration? = Duration.ofDays(180)
    ) = ParsedSecurityContextConfig(
        provider = provider,
        headers = emptyMap(),
        unknownRetention = unknownRetention,
        knownRetention = knownRetention
    )

    private fun parsedConfig(
        pagination: ParsedPaginationConfig = ParsedPaginationConfig(20, 100),
        securityContext: ParsedSecurityContextConfig = parsedSecurityContext(),
        accessReviewWebhook: ParsedAccessReviewWebhookConfig = ParsedAccessReviewWebhookConfig(
            timeout = Duration.ofSeconds(2),
            pastContexts = 10
        )
    ): ParsedAdvancedConfig {
        val hash = ParsedHashConfig(
            costParameter = 16_384,
            blockSize = 8,
            parallelizationParameter = 1,
            saltLength = 256,
            keyLength = 32
        )
        return ParsedAdvancedConfig(
            keysGenerationStrategyId = CryptoKeysGenerationStrategyId.AUTO_INCREMENT,
            publicJwtAlgorithm = JwtAlgorithm.ES256,
            accessJwtAlgorithm = JwtAlgorithm.ES256,
            privateJwtAlgorithm = JwtAlgorithm.HS256,
            hash = hash,
            invitation = ParsedInvitationConfig(
                tokenLength = 32,
                defaultExpiration = Duration.ofDays(7),
                maxExpiration = Duration.ofDays(30),
                hash = hash
            ),
            validationCode = ParsedValidationCodeConfig(
                expiration = Duration.ofMinutes(10),
                length = 6,
                resendDelay = Duration.ofMinutes(1)
            ),
            webhookTimeout = Duration.ofSeconds(5),
            accessReviewWebhook = accessReviewWebhook,
            pagination = pagination,
            securityContext = securityContext
        )
    }
}

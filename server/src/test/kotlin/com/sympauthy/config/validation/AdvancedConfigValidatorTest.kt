package com.sympauthy.config.validation

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.security.EdgeProvider
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ConfiguredImplementation
import com.sympauthy.config.parsing.ParsedAdvancedConfig
import com.sympauthy.config.parsing.ParsedHashConfig
import com.sympauthy.config.parsing.ParsedInvitationConfig
import com.sympauthy.config.parsing.ParsedPaginationConfig
import com.sympauthy.config.parsing.ParsedSecurityContextConfig
import com.sympauthy.config.parsing.ParsedSecurityContextHeaders
import com.sympauthy.config.parsing.ParsedValidationCodeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class AdvancedConfigValidatorTest {

    private val validator = AdvancedConfigValidator()

    @Test
    fun `validate - Return no configuration when a pagination bound did not parse`() {
        val ctx = ConfigParsingContext()
        ctx.addError(configExceptionOf("advanced.pagination.max-size", "config.missing"))

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(20, null)))

        assertNull(config)
    }

    @Test
    fun `validate - Keep the configured pagination bounds`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(50, 500)))

        assertNotNull(config)
        assertEquals(50, config!!.pagination.defaultSize)
        assertEquals(500, config.pagination.maxSize)
    }

    @Test
    fun `validate - Reject a default page size below one`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(0, 100)))

        assertNull(config)
        assertEquals(
            listOf("config.advanced.pagination.invalid_default_size"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Reject a maximum page size below one`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(20, 0)))

        assertNull(config)
        assertEquals(
            listOf("config.advanced.pagination.invalid_max_size"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Reject a default page size above the maximum`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(ParsedPaginationConfig(200, 100)))

        assertNull(config)
        assertEquals(
            listOf("config.advanced.pagination.default_exceeds_max"),
            ctx.errors.map { it.messageId }
        )
    }

    @Test
    fun `validate - Reject a provider listed twice`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(providers = listOf("nginx", "gcp", "nginx")))

        assertNull(config)
        assertEquals(
            listOf("advanced.security-context.providers[2]" to "config.advanced.security_context.duplicate_provider"),
            ctx.errors.map { it.key to it.messageId }
        )
        assertEquals(mapOf("provider" to "nginx"), ctx.errors.single().values)
    }

    @Test
    fun `validate - Keep the providers in the order they were written`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(providers = listOf("gcp", "nginx")))

        assertNotNull(config)
        assertEquals(listOf("gcp", "nginx"), config!!.securityContext.providers.map { it.qualifier })
    }

    @Test
    fun `validate - Name no proxy and no header where the deployment configured nothing`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig())

        assertNotNull(config)
        assertEquals(emptyList<String>(), config!!.securityContext.providers.map { it.qualifier })
        assertEquals(false, config.securityContext.autoDetect)
        assertNull(config.securityContext.headers.clientIp)
    }

    @Test
    fun `validate - Leave auto-detect off where the deployment did not write it`() {
        val ctx = ConfigParsingContext()

        val config = validator.validate(ctx, parsedConfig(autoDetect = null))

        assertNotNull(config)
        assertEquals(false, config!!.securityContext.autoDetect)
    }

    private fun parsedConfig(
        pagination: ParsedPaginationConfig = ParsedPaginationConfig(20, 100),
        autoDetect: Boolean? = null,
        providers: List<String> = emptyList()
    ): ParsedAdvancedConfig {
        val hash = ParsedHashConfig(
            costParameter = 16_384,
            blockSize = 8,
            parallelizationParameter = 1,
            saltLength = 256,
            keyLength = 32
        )
        return ParsedAdvancedConfig(
            keysGenerationStrategy = ConfiguredImplementation(CryptoKeysGenerationStrategy::class, "auto-increment"),
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
            pagination = pagination,
            securityContext = ParsedSecurityContextConfig(
                autoDetect = autoDetect,
                providers = providers.map { ConfiguredImplementation(EdgeProvider::class, it) },
                headers = ParsedSecurityContextHeaders(
                    clientIp = null,
                    countryCode = null,
                    regionCode = null,
                    region = null,
                    city = null,
                    postalCode = null,
                    timeZone = null
                )
            )
        )
    }
}

package com.sympauthy.config.validation

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategyQualifiers
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedAdvancedConfig
import com.sympauthy.config.parsing.ParsedHashConfig
import com.sympauthy.config.parsing.ParsedInvitationConfig
import com.sympauthy.config.parsing.ParsedPaginationConfig
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

    private fun parsedConfig(pagination: ParsedPaginationConfig): ParsedAdvancedConfig {
        val hash = ParsedHashConfig(
            costParameter = 16384,
            blockSize = 8,
            parallelizationParameter = 1,
            saltLength = 256,
            keyLength = 32
        )
        return ParsedAdvancedConfig(
            keysGenerationStrategyId = CryptoKeysGenerationStrategyQualifiers.AUTO_INCREMENT,
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
            pagination = pagination
        )
    }
}

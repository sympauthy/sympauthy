package com.sympauthy.config.validation

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.AuthorizationWebhookAdvancedConfig
import com.sympauthy.config.model.CleanupConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.HashConfig
import com.sympauthy.config.model.InvitationAdvancedConfig
import com.sympauthy.config.model.PaginationConfig
import com.sympauthy.config.model.ValidationCodeConfig
import com.sympauthy.config.parsing.ParsedAdvancedConfig
import com.sympauthy.config.parsing.ParsedCleanupConfig
import com.sympauthy.config.parsing.ParsedHashConfig
import com.sympauthy.config.parsing.ParsedInvitationConfig
import com.sympauthy.config.parsing.ParsedPaginationConfig
import com.sympauthy.config.parsing.ParsedValidationCodeConfig
import com.sympauthy.config.properties.CleanupConfigurationProperties.Companion.CLEANUP_KEY
import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import com.sympauthy.config.properties.InvitationHashConfigurationProperties.Companion.INVITATION_HASH_KEY
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import com.sympauthy.config.properties.PaginationConfigurationProperties.Companion.PAGINATION_KEY
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties.Companion.VALIDATION_CODE_KEY
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class AdvancedConfigValidator {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedAdvancedConfig
    ): EnabledAdvancedConfig? {
        validatePublicKeyAlgorithm(ctx, parsed.publicJwtAlgorithm)
        validateAccessKeyAlgorithm(ctx, parsed.accessJwtAlgorithm)
        validatePrivateKeyAlgorithm(ctx, parsed.privateJwtAlgorithm)
        val hashConfig = validateHashConfig(ctx, HASH_KEY, parsed.hash)
        val invitationConfig = validateInvitationConfig(ctx, parsed.invitation)
        val validationCodeConfig = validateValidationCodeConfig(ctx, parsed.validationCode)
        val webhookConfig = AuthorizationWebhookAdvancedConfig(
            timeout = parsed.webhookTimeout ?: DEFAULT_WEBHOOK_TIMEOUT
        )
        val paginationConfig = validatePaginationConfig(ctx, parsed.pagination)
        val cleanupConfig = validateCleanupConfig(ctx, parsed.cleanup)

        if (ctx.hasErrors) return null
        return EnabledAdvancedConfig(
            keysGenerationStrategyId = parsed.keysGenerationStrategyId!!,
            publicJwtAlgorithm = parsed.publicJwtAlgorithm!!,
            accessJwtAlgorithm = parsed.accessJwtAlgorithm!!,
            privateJwtAlgorithm = parsed.privateJwtAlgorithm!!,
            hashConfig = hashConfig!!,
            invitationConfig = invitationConfig!!,
            validationCode = validationCodeConfig!!,
            authorizationWebhook = webhookConfig,
            pagination = paginationConfig!!,
            cleanup = cleanupConfig!!
        )
    }

    private fun validatePublicKeyAlgorithm(ctx: ConfigParsingContext, algorithm: JwtAlgorithm?) {
        if (algorithm != null && !algorithm.keyAlgorithm.supportsPublicKey) {
            ctx.addError(
                configExceptionOf(
                    "$JWT_KEY.public-alg",
                    "config.advanced.jwt.public_alg.unsupported_public_key",
                    "algorithms" to JwtAlgorithm.entries
                        .filter { it.keyAlgorithm.supportsPublicKey }
                        .joinToString(", ")
                )
            )
        }
    }

    private fun validateAccessKeyAlgorithm(ctx: ConfigParsingContext, algorithm: JwtAlgorithm?) {
        if (algorithm != null && !algorithm.keyAlgorithm.supportsPublicKey) {
            ctx.addError(
                configExceptionOf(
                    "$JWT_KEY.access-alg",
                    "config.advanced.jwt.access_alg.unsupported_public_key",
                    "algorithms" to JwtAlgorithm.entries
                        .filter { it.keyAlgorithm.supportsPublicKey }
                        .joinToString(", ")
                )
            )
        }
    }

    private fun validatePrivateKeyAlgorithm(ctx: ConfigParsingContext, algorithm: JwtAlgorithm?) {
        if (algorithm != null && !algorithm.deterministic) {
            ctx.addError(
                configExceptionOf(
                    "$JWT_KEY.private-alg",
                    "config.advanced.jwt.private_alg.not_deterministic",
                    "algorithms" to JwtAlgorithm.entries
                        .filter { it.deterministic }
                        .joinToString(", ")
                )
            )
        }
    }

    private fun validateCleanupConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedCleanupConfig
    ): CleanupConfig? {
        val subCtx = ctx.child()

        // A run binds one parameter per row into the IN list of every delete it issues, so the upper
        // bound is the database's rather than a threshold nobody argued for.
        if (parsed.batchSize != null && parsed.batchSize !in 1..MAX_BIND_PARAMETERS) {
            subCtx.addError(
                configExceptionOf(
                    "$CLEANUP_KEY.batch-size", "config.advanced.cleanup.invalid_batch_size",
                    "max" to MAX_BIND_PARAMETERS
                )
            )
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || parsed.batchSize == null) {
            return null
        }
        return CleanupConfig(
            batchSize = parsed.batchSize
        )
    }

    private fun validatePaginationConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedPaginationConfig
    ): PaginationConfig? {
        val subCtx = ctx.child()

        if (parsed.defaultSize != null && parsed.defaultSize <= 0) {
            subCtx.addError(
                configExceptionOf("$PAGINATION_KEY.default-size", "config.advanced.pagination.invalid_default_size")
            )
        }

        if (parsed.maxSize != null && parsed.maxSize <= 0) {
            subCtx.addError(
                configExceptionOf("$PAGINATION_KEY.max-size", "config.advanced.pagination.invalid_max_size")
            )
        }

        if (parsed.defaultSize != null && parsed.maxSize != null &&
            parsed.defaultSize > 0 && parsed.maxSize > 0 && parsed.defaultSize > parsed.maxSize
        ) {
            subCtx.addError(
                configExceptionOf("$PAGINATION_KEY.default-size", "config.advanced.pagination.default_exceeds_max")
            )
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || parsed.defaultSize == null || parsed.maxSize == null) {
            return null
        }
        return PaginationConfig(
            defaultSize = parsed.defaultSize,
            maxSize = parsed.maxSize
        )
    }

    private fun validateHashConfig(
        ctx: ConfigParsingContext,
        configKeyPrefix: String,
        parsed: ParsedHashConfig
    ): HashConfig? {
        val subCtx = ctx.child()

        val costParameter = parsed.costParameter
        if (costParameter != null && (costParameter !in 2..65_535 || !isPowerOf2(costParameter))) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.cost-parameter", "config.advanced.hash.invalid_cost_parameter")
            )
        }

        if (parsed.blockSize != null && parsed.blockSize <= 0) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.block-size", "config.advanced.hash.invalid_block_size")
            )
        }

        if (costParameter != null && parsed.parallelizationParameter != null) {
            val max = Int.MAX_VALUE / (128 * costParameter * 8)
            if (parsed.parallelizationParameter !in 1..max) {
                subCtx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.parallelization-parameter",
                        "config.advanced.hash.invalid_parallelization_parameter",
                        "max" to max
                    )
                )
            }
        }

        if (parsed.saltLength != null && (parsed.saltLength <= 0 && parsed.saltLength % 8 != 0)) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.salt-length", "config.advanced.hash.invalid_salt_length")
            )
        }

        if (parsed.keyLength != null && parsed.keyLength <= 0) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.key-length", "config.advanced.hash.invalid_key_length")
            )
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || parsed.costParameter == null || parsed.blockSize == null ||
            parsed.parallelizationParameter == null || parsed.saltLength == null || parsed.keyLength == null
        ) {
            return null
        }
        return HashConfig(
            costParameter = parsed.costParameter,
            blockSize = parsed.blockSize,
            parallelizationParameter = parsed.parallelizationParameter,
            saltLengthInBytes = parsed.saltLength / 8,
            keyLengthInBytes = parsed.keyLength
        )
    }

    private fun validateInvitationConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedInvitationConfig
    ): InvitationAdvancedConfig? {
        val subCtx = ctx.child()

        if (parsed.tokenLength != null && parsed.tokenLength <= 0) {
            subCtx.addError(
                configExceptionOf(
                    "$INVITATION_KEY.token-length",
                    "config.advanced.invitation.invalid_token_length"
                )
            )
        }

        if (parsed.defaultExpiration != null && parsed.maxExpiration != null
            && parsed.defaultExpiration > parsed.maxExpiration
        ) {
            subCtx.addError(
                configExceptionOf(
                    "$INVITATION_KEY.default-expiration",
                    "config.advanced.invitation.default_exceeds_max"
                )
            )
        }

        val hashConfig = validateHashConfig(subCtx, INVITATION_HASH_KEY, parsed.hash)

        ctx.merge(subCtx)
        if (subCtx.hasErrors || parsed.tokenLength == null
            || parsed.defaultExpiration == null || parsed.maxExpiration == null
            || hashConfig == null
        ) {
            return null
        }
        return InvitationAdvancedConfig(
            tokenLengthInBytes = parsed.tokenLength,
            defaultExpiration = parsed.defaultExpiration,
            maxExpiration = parsed.maxExpiration,
            hashConfig = hashConfig
        )
    }

    private fun validateValidationCodeConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedValidationCodeConfig
    ): ValidationCodeConfig? {
        if (parsed.length != null && parsed.length <= 0) {
            ctx.addError(
                configExceptionOf(
                    "$VALIDATION_CODE_KEY.length",
                    "config.advanced.validation_code.invalid_length"
                )
            )
        }
        if (ctx.hasErrors || parsed.expiration == null || parsed.length == null) {
            return null
        }
        return ValidationCodeConfig(
            expiration = parsed.expiration,
            length = parsed.length,
            resendDelay = parsed.resendDelay
        )
    }

    private fun isPowerOf2(var0: Int): Boolean = (var0 and var0 - 1) == 0

    companion object {
        private val DEFAULT_WEBHOOK_TIMEOUT: Duration = Duration.ofSeconds(5)

        /** Bind parameters PostgreSQL's extended protocol admits in one statement. */
        private const val MAX_BIND_PARAMETERS = 65_535
    }
}

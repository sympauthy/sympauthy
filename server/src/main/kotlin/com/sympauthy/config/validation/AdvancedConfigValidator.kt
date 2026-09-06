package com.sympauthy.config.validation

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.AuthorizationWebhookAdvancedConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.HashConfig
import com.sympauthy.config.model.InvitationAdvancedConfig
import com.sympauthy.config.model.PaginationConfig
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.ValidationCodeConfig
import com.sympauthy.config.parsing.ParsedAdvancedConfig
import com.sympauthy.config.parsing.ParsedHashConfig
import com.sympauthy.config.parsing.ParsedInvitationConfig
import com.sympauthy.config.parsing.ParsedPaginationConfig
import com.sympauthy.config.parsing.ParsedSecurityContextConfig
import com.sympauthy.config.parsing.ParsedValidationCodeConfig
import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import com.sympauthy.config.properties.InvitationHashConfigurationProperties.Companion.INVITATION_HASH_KEY
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import com.sympauthy.config.properties.PaginationConfigurationProperties.Companion.PAGINATION_KEY
import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties.Companion.VALIDATION_CODE_KEY
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class AdvancedConfigValidator {

    /**
     * The enabled configuration [parsed] describes, or null where it describes none.
     *
     * [profilesByName] is every extraction published for a proxy, by the name a deployment names it
     * with, which the factory resolves and hands over: the set of providers is the set implemented,
     * and this is where a deployment naming one outside it is refused.
     */
    fun validate(
        ctx: ConfigParsingContext,
        parsed: ParsedAdvancedConfig,
        profilesByName: Map<String, EdgeProviderProfile>
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
        val securityContextConfig = validateSecurityContextConfig(ctx, parsed.securityContext, profilesByName)

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
            securityContext = securityContextConfig!!
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

    private fun validateSecurityContextConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedSecurityContextConfig,
        profilesByName: Map<String, EdgeProviderProfile>
    ): SecurityContextConfig? {
        val subCtx = ctx.child()

        val profile = parsed.provider?.let(profilesByName::get)
        if (parsed.provider != null && profile == null) {
            subCtx.addError(
                configExceptionOf(
                    "$SECURITY_CONTEXT_KEY.provider",
                    "config.advanced.security_context.unknown_provider",
                    "provider" to parsed.provider,
                    "supportedProviders" to profilesByName.keys.sorted().joinToString(", ")
                )
            )
        }

        if (parsed.unknownRetention != null && !parsed.unknownRetention.isPositive()) {
            subCtx.addError(
                configExceptionOf(
                    "$SECURITY_CONTEXT_KEY.unknown-retention",
                    "config.advanced.security_context.invalid_unknown_retention"
                )
            )
        }

        if (parsed.knownRetention != null && !parsed.knownRetention.isPositive()) {
            subCtx.addError(
                configExceptionOf(
                    "$SECURITY_CONTEXT_KEY.known-retention",
                    "config.advanced.security_context.invalid_known_retention"
                )
            )
        }

        if (parsed.unknownRetention != null && parsed.knownRetention != null &&
            parsed.unknownRetention.isPositive() && parsed.knownRetention.isPositive() &&
            parsed.unknownRetention > parsed.knownRetention
        ) {
            subCtx.addError(
                configExceptionOf(
                    "$SECURITY_CONTEXT_KEY.unknown-retention",
                    "config.advanced.security_context.unknown_exceeds_known"
                )
            )
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || profile == null ||
            parsed.unknownRetention == null || parsed.knownRetention == null
        ) {
            return null
        }
        return SecurityContextConfig(
            profile = profile,
            headers = parsed.headers,
            unknownRetention = parsed.unknownRetention,
            knownRetention = parsed.knownRetention
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

    private fun Duration.isPositive(): Boolean = !isNegative && !isZero

    companion object {
        private val DEFAULT_WEBHOOK_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}

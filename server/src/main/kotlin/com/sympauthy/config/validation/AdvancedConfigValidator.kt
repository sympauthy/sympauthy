package com.sympauthy.config.validation

import com.sympauthy.business.jwt.model.JwtAlgorithm
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.AuthorizationWebhookAdvancedConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.HashConfig
import com.sympauthy.config.model.InvitationAdvancedConfig
import com.sympauthy.config.model.PaginationConfig
import com.sympauthy.config.model.QueueConfig
import com.sympauthy.config.model.SecurityContextConfig
import com.sympauthy.config.model.SecurityContextGeoConfig
import com.sympauthy.config.model.SecurityContextGeoHeadersConfig
import com.sympauthy.config.model.SecurityContextIpConfig
import com.sympauthy.config.model.ValidationCodeConfig
import com.sympauthy.config.parsing.ParsedAdvancedConfig
import com.sympauthy.config.parsing.ParsedHashConfig
import com.sympauthy.config.parsing.ParsedInvitationConfig
import com.sympauthy.config.parsing.ParsedPaginationConfig
import com.sympauthy.config.parsing.ParsedQueueConfig
import com.sympauthy.config.parsing.ParsedSecurityContextConfig
import com.sympauthy.config.parsing.ParsedSecurityContextGeoConfig
import com.sympauthy.config.parsing.ParsedSecurityContextIpConfig
import com.sympauthy.config.parsing.ParsedValidationCodeConfig
import com.sympauthy.config.properties.HashConfigurationProperties.Companion.HASH_KEY
import com.sympauthy.config.properties.InvitationConfigurationProperties.Companion.INVITATION_KEY
import com.sympauthy.config.properties.InvitationHashConfigurationProperties.Companion.INVITATION_HASH_KEY
import com.sympauthy.config.properties.JwtConfigurationProperties.Companion.JWT_KEY
import com.sympauthy.config.properties.PaginationConfigurationProperties.Companion.PAGINATION_KEY
import com.sympauthy.config.properties.QueueConfigurationProperties.Companion.QUEUE_KEY
import com.sympauthy.config.properties.QueueRetryConfigurationProperties.Companion.QUEUE_RETRY_KEY
import com.sympauthy.config.properties.SecurityContextConfigurationProperties.Companion.SECURITY_CONTEXT_KEY
import com.sympauthy.config.properties.SecurityContextGeoConfigurationProperties.Companion.SECURITY_CONTEXT_GEO_KEY
import com.sympauthy.config.properties.SecurityContextGeoHeadersConfigurationProperties.Companion.GEO_HEADERS_KEY
import com.sympauthy.config.properties.SecurityContextIpConfigurationProperties.Companion.SECURITY_CONTEXT_IP_KEY
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
        val securityContextConfig = validateSecurityContextConfig(ctx, parsed.securityContext)
        val queueConfig = validateQueueConfig(ctx, parsed.queue)

        if (ctx.hasErrors) return null
        return EnabledAdvancedConfig(
            keysGenerationStrategy = parsed.keysGenerationStrategy!!,
            publicJwtAlgorithm = parsed.publicJwtAlgorithm!!,
            accessJwtAlgorithm = parsed.accessJwtAlgorithm!!,
            privateJwtAlgorithm = parsed.privateJwtAlgorithm!!,
            hashConfig = hashConfig!!,
            invitationConfig = invitationConfig!!,
            validationCode = validationCodeConfig!!,
            authorizationWebhook = webhookConfig,
            pagination = paginationConfig!!,
            securityContext = securityContextConfig!!,
            queue = queueConfig!!
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

    private fun validateSecurityContextConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedSecurityContextConfig
    ): SecurityContextConfig? {
        val ip = validateSecurityContextIpConfig(ctx, parsed.ip)
        val geo = validateSecurityContextGeoConfig(ctx, parsed.geo)
        val retention = validateKnownUserRetention(ctx, parsed.knownUserRetention)
        if (ip == null || geo == null || retention == null) {
            return null
        }
        return SecurityContextConfig(ip = ip, geo = geo, knownUserRetention = retention)
    }

    /**
     * How long a place is kept, the default where the deployment wrote none, or null where it is
     * not a lifetime.
     */
    private fun validateKnownUserRetention(ctx: ConfigParsingContext, parsed: Duration?): Duration? {
        return validatePositiveDuration(
            ctx, "$SECURITY_CONTEXT_KEY.known-user-retention", parsed ?: DEFAULT_KNOWN_USER_RETENTION
        )
    }

    private fun validateSecurityContextIpConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedSecurityContextIpConfig
    ): SecurityContextIpConfig? {
        val subCtx = ctx.child()
        validateHeaderName(subCtx, "$SECURITY_CONTEXT_IP_KEY.header", parsed.header)
        ctx.merge(subCtx)
        if (subCtx.hasErrors) {
            return null
        }
        return SecurityContextIpConfig(provider = parsed.provider, header = parsed.header)
    }

    /**
     * The edges named, once each, and every header named for a field spelled as a header name. A
     * qualifier written twice is refused against the index of the repeat rather than folded away.
     */
    private fun validateSecurityContextGeoConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedSecurityContextGeoConfig
    ): SecurityContextGeoConfig? {
        val subCtx = ctx.child()

        val seen = mutableSetOf<String>()
        parsed.providers.forEach { provider ->
            if (!seen.add(provider.implementation.qualifier)) {
                subCtx.addError(
                    configExceptionOf(
                        "$SECURITY_CONTEXT_GEO_KEY.providers[${provider.index}]",
                        "config.advanced.security_context.duplicate_provider",
                        "provider" to provider.implementation.qualifier
                    )
                )
            }
        }

        val headers = parsed.headers
        validateHeaderName(subCtx, "$GEO_HEADERS_KEY.country-code", headers.countryCode)
        validateHeaderName(subCtx, "$GEO_HEADERS_KEY.region-code", headers.regionCode)
        validateHeaderName(subCtx, "$GEO_HEADERS_KEY.region", headers.region)
        validateHeaderName(subCtx, "$GEO_HEADERS_KEY.city", headers.city)
        validateHeaderName(subCtx, "$GEO_HEADERS_KEY.postal-code", headers.postalCode)
        validateHeaderName(subCtx, "$GEO_HEADERS_KEY.time-zone", headers.timeZone)

        ctx.merge(subCtx)
        if (subCtx.hasErrors) {
            return null
        }
        return SecurityContextGeoConfig(
            autoDetect = parsed.autoDetect ?: false,
            providers = parsed.providers.map { it.implementation },
            headers = SecurityContextGeoHeadersConfig(
                countryCode = headers.countryCode,
                regionCode = headers.regionCode,
                region = headers.region,
                city = headers.city,
                postalCode = headers.postalCode,
                timeZone = headers.timeZone
            )
        )
    }

    /**
     * Refuses [name] where it is not a header name any request could carry. RFC 9110 section 5.1
     * calls a field name a token, and [HEADER_NAME] is that set.
     */
    private fun validateHeaderName(ctx: ConfigParsingContext, key: String, name: String?) {
        if (name != null && !HEADER_NAME.matches(name)) {
            ctx.addError(
                configExceptionOf(key, "config.advanced.security_context.invalid_header_name", "header" to name)
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

    /**
     * The queue's timings, each greater than zero, and ordered as the retries need them: a poll no longer than
     * the first retry, which waits for one, and a first retry no longer than the longest.
     */
    private fun validateQueueConfig(
        ctx: ConfigParsingContext,
        parsed: ParsedQueueConfig
    ): QueueConfig? {
        val subCtx = ctx.child()
        val pollInterval = validatePositiveDuration(subCtx, "$QUEUE_KEY.poll-interval", parsed.pollInterval)
        val shutdownGrace = validatePositiveDuration(subCtx, "$QUEUE_KEY.shutdown-grace", parsed.shutdownGrace)
        val firstDelay = validatePositiveDuration(subCtx, "$QUEUE_RETRY_KEY.first-delay", parsed.retryFirstDelay)
        val maxDelay = validatePositiveDuration(subCtx, "$QUEUE_RETRY_KEY.max-delay", parsed.retryMaxDelay)

        if (parsed.batchSize != null && parsed.batchSize <= 0) {
            subCtx.addError(
                configExceptionOf("$QUEUE_KEY.batch-size", "config.advanced.queue.invalid_batch_size")
            )
        }
        if (firstDelay != null && maxDelay != null && firstDelay > maxDelay) {
            subCtx.addError(
                configExceptionOf("$QUEUE_RETRY_KEY.first-delay", "config.advanced.queue.first_delay_exceeds_max")
            )
        }
        if (pollInterval != null && firstDelay != null && pollInterval > firstDelay) {
            subCtx.addError(
                configExceptionOf(
                    "$QUEUE_KEY.poll-interval",
                    "config.advanced.queue.poll_interval_exceeds_first_delay"
                )
            )
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || pollInterval == null || parsed.batchSize == null || shutdownGrace == null ||
            firstDelay == null || maxDelay == null
        ) {
            return null
        }
        return QueueConfig(
            pollInterval = pollInterval,
            batchSize = parsed.batchSize,
            shutdownGrace = shutdownGrace,
            retryFirstDelay = firstDelay,
            retryMaxDelay = maxDelay
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

        validatePositiveDuration(subCtx, "$INVITATION_KEY.default-expiration", parsed.defaultExpiration)
        validatePositiveDuration(subCtx, "$INVITATION_KEY.max-expiration", parsed.maxExpiration)
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
        validatePositiveDuration(ctx, "$VALIDATION_CODE_KEY.expiration", parsed.expiration)
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

        /**
         * Six months, which is long enough for a place somebody uses a few times a year to still be one
         * this server recognises, and short enough that an operator who never looks is not holding a
         * year of addresses.
         */
        private val DEFAULT_KNOWN_USER_RETENTION: Duration = Duration.ofDays(180)

        /**
         * The shape of an HTTP field name, which RFC 9110 section 5.1 defines as a token.
         */
        private val HEADER_NAME = Regex("[!#\u0024%&'*+\\-.^_`|~0-9A-Za-z]+")
    }
}

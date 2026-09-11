package com.sympauthy.config.model

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import java.time.Duration

/**
 * A complete advanced configuration whose values are the shipped ones, so that a test names only the
 * domain it is about.
 *
 * It is built rather than mocked because a stub a test never reaches fails the unnecessary-stub
 * check, and most of the tests taking one of these never read the half they did not name.
 */
fun advancedConfigOf(
    pagination: PaginationConfig = PaginationConfig(defaultSize = 20, maxSize = 100),
    securityContext: SecurityContextConfig = trustlessSecurityContext()
): EnabledAdvancedConfig {
    val hashConfig = HashConfig(
        costParameter = 16_384,
        blockSize = 8,
        parallelizationParameter = 1,
        saltLengthInBytes = 32,
        keyLengthInBytes = 32
    )
    return EnabledAdvancedConfig(
        keysGenerationStrategy = ConfiguredImplementation(CryptoKeysGenerationStrategy::class, "auto-increment"),
        publicJwtAlgorithm = JwtAlgorithm.ES256,
        accessJwtAlgorithm = JwtAlgorithm.ES256,
        privateJwtAlgorithm = JwtAlgorithm.HS256,
        hashConfig = hashConfig,
        invitationConfig = InvitationAdvancedConfig(
            tokenLengthInBytes = 32,
            defaultExpiration = Duration.ofDays(7),
            maxExpiration = Duration.ofDays(30),
            hashConfig = hashConfig
        ),
        validationCode = ValidationCodeConfig(
            length = 6,
            resendDelay = Duration.ofMinutes(1),
            expiration = Duration.ofMinutes(10)
        ),
        authorizationWebhook = AuthorizationWebhookAdvancedConfig(
            timeout = Duration.ofSeconds(5)
        ),
        pagination = pagination,
        securityContext = securityContext
    )
}

/**
 * What a deployment that configured nothing has: no proxy named, no auto-detection, no header of its
 * own.
 */
fun trustlessSecurityContext() = SecurityContextConfig(
    autoDetect = false,
    providers = emptyList(),
    headers = noNamedHeaders()
)

fun noNamedHeaders() = SecurityContextHeadersConfig(
    clientIp = null,
    countryCode = null,
    regionCode = null,
    region = null,
    city = null,
    postalCode = null,
    timeZone = null
)

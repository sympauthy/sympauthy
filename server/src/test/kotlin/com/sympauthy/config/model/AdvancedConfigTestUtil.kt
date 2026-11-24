package com.sympauthy.config.model

import com.sympauthy.business.jwt.model.JwtAlgorithm
import com.sympauthy.business.key.model.CryptoKeysGenerationStrategy
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
    securityContext: SecurityContextConfig = trustlessSecurityContext(),
    queue: QueueConfig = shippedQueue()
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
        securityContext = securityContext,
        queue = queue
    )
}

/**
 * The queue's timings as the `default` environment ships them.
 */
fun shippedQueue() = QueueConfig(
    pollInterval = Duration.ofSeconds(5),
    batchSize = 50,
    shutdownGrace = Duration.ofSeconds(5),
    retryFirstDelay = Duration.ofSeconds(10),
    retryMaxDelay = Duration.ofMinutes(2)
)

/**
 * What a deployment that configured nothing has: no proxy named for the address, no edge read for a
 * location, and no header of its own.
 */
fun trustlessSecurityContext() = SecurityContextConfig(
    ip = SecurityContextIpConfig(provider = null, header = null),
    geo = SecurityContextGeoConfig(autoDetect = false, providers = emptyList(), headers = noNamedGeoHeaders()),
    knownUserRetention = Duration.ofDays(180)
)

fun noNamedGeoHeaders() = SecurityContextGeoHeadersConfig(
    countryCode = null,
    regionCode = null,
    region = null,
    city = null,
    postalCode = null,
    timeZone = null
)

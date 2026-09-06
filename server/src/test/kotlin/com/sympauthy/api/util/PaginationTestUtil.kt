package com.sympauthy.api.util

import com.sympauthy.business.model.jwt.JwtAlgorithm
import com.sympauthy.business.model.key.CryptoKeysGenerationStrategyId
import com.sympauthy.config.model.AuthorizationWebhookAdvancedConfig
import com.sympauthy.config.model.CleanupConfig
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.HashConfig
import com.sympauthy.config.model.InvitationAdvancedConfig
import com.sympauthy.config.model.PaginationConfig
import com.sympauthy.config.model.ValidationCodeConfig
import java.time.Duration

const val TEST_DEFAULT_PAGE_SIZE = 20
const val TEST_MAX_PAGE_SIZE = 100

/**
 * A [PaginationUtil] bounded by the values a deployment gets when it configures nothing, for the
 * controllers whose tests need paging to work rather than to be configured.
 */
fun defaultPaginationUtil(): PaginationUtil =
    paginationUtilOf(defaultSize = TEST_DEFAULT_PAGE_SIZE, maxSize = TEST_MAX_PAGE_SIZE)

/**
 * The advanced configuration is built rather than mocked because a stub the test never reaches is
 * a failure under the unnecessary-stub check, and most of these tests never page anything.
 */
fun paginationUtilOf(
    defaultSize: Int,
    maxSize: Int
): PaginationUtil {
    val hashConfig = HashConfig(
        costParameter = 16_384,
        blockSize = 8,
        parallelizationParameter = 1,
        saltLengthInBytes = 32,
        keyLengthInBytes = 32
    )
    return PaginationUtil(
        EnabledAdvancedConfig(
            keysGenerationStrategyId = CryptoKeysGenerationStrategyId.AUTO_INCREMENT,
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
            pagination = PaginationConfig(defaultSize = defaultSize, maxSize = maxSize),
            cleanup = CleanupConfig(batchSize = 1000)
        )
    )
}

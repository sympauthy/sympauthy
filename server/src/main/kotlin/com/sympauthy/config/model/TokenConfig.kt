package com.sympauthy.config.model

import java.time.Duration

data class TokenConfig(
    val accessExpiration: Duration,
    val idExpiration: Duration,
    val refreshEnabled: Boolean,
    val refreshExpiration: Duration?,
    /**
     * How long after a grant is opened it can no longer be refreshed, whatever its refresh token's own
     * expiry. Null where a grant has no lifetime of its own.
     */
    val grantExpiration: Duration?,
    val dpopRequired: Boolean
)

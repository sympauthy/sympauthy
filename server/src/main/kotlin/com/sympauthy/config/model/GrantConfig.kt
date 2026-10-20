package com.sympauthy.config.model

import java.time.Duration

data class GrantConfig(
    /**
     * How long after a grant is opened it can no longer be refreshed, whatever its refresh token's own
     * expiry, nor its code exchanged. Null where a grant has no lifetime of its own.
     */
    val expiration: Duration?
)

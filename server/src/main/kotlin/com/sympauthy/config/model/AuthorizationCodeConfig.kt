package com.sympauthy.config.model

import java.time.Duration

data class AuthorizationCodeConfig(
    /**
     * How long an authorization code can be exchanged for tokens, from when the authorization hands it out.
     * It does not depend on the interactive flow session that issued the code, which may be collected first.
     */
    val expiration: Duration
)

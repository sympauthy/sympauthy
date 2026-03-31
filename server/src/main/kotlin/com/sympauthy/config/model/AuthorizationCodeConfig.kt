package com.sympauthy.config.model

import java.time.Duration

/**
 * Configuration for the authorization code grant flow.
 */
data class AuthorizationCodeConfig(
    /**
     * Maximum duration an authorization attempt is valid before it expires and is cleaned up.
     * The end-user must complete the entire authorization flow (sign-in, claims collection, MFA, etc.)
     * within this duration.
     */
    val expiration: Duration
)
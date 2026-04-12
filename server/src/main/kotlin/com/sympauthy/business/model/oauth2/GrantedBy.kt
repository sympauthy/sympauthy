package com.sympauthy.business.model.oauth2

/**
 * Describes how the grantable scopes were granted during the authorization process.
 */
enum class GrantedBy {
    /**
     * All scopes were auto-granted (built-in scopes with autoGranted flag).
     */
    AUTO,

    /**
     * At least one scope was granted through granting rules or default behavior.
     */
    RULE,

    /**
     * At least one scope was granted through an authorization webhook delegation.
     */
    WEBHOOK
}

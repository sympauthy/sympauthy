package com.sympauthy.business.consent.model

/**
 * Describes how the consentable scopes were consented during the authorization process.
 */
enum class ConsentedBy {
    /**
     * Scopes were auto-consented without user interaction (no consent screen).
     */
    AUTO,

    /**
     * User explicitly consented via a consent screen.
     */
    USER
}

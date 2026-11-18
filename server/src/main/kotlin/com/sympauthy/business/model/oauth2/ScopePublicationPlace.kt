package com.sympauthy.business.model.oauth2

/**
 * A place this authorization server publishes a scope in, written `published-in` in the configuration as
 * [a claim's][com.sympauthy.business.model.user.claim.ClaimPublicationPlace] is. See
 * `docs/design/security.md`.
 */
enum class ScopePublicationPlace {
    /**
     * `scopes_supported` in the discovery document.
     */
    DISCOVERY
}

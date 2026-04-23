package com.sympauthy.business.model.audience

/**
 * An audience that groups client applications.
 *
 * Clients belonging to the same audience share user consent, and scopes/claims can be
 * optionally restricted to a specific audience for isolation.
 */
data class Audience(
    /**
     * Unique identifier of this audience, as defined in the YAML configuration.
     */
    val id: String,
    /**
     * Value used as the `aud` claim in access and refresh tokens issued for clients
     * belonging to this audience. Defaults to [id] if not explicitly configured.
     */
    val tokenAudience: String
)

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
    val tokenAudience: String,
    /**
     * Whether open registration is enabled for this audience.
     * When true, any user can create an account through the sign-up flow.
     */
    val signUpEnabled: Boolean = true,
    /**
     * Whether invitation-based registration is enabled for this audience.
     * When true, invitations can be created and used to register.
     */
    val invitationEnabled: Boolean = false
)

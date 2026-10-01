package com.sympauthy.business.model.user.claim

/**
 * A place this authorization server publishes a claim in.
 *
 * It answers where a claim goes and not who may know it: what a caller is entitled to is the claim's
 * [ClaimAcl], and the places a claim names only narrow what that already permits.
 *
 * Four of them carry a value and one advertises a name, which is what [carriesAValue] separates. A
 * claim advertised and carried nowhere is a name no client could ever obtain a value for, and
 * [com.sympauthy.config.validation.ClaimsConfigValidator] refuses one at startup.
 */
enum class ClaimPublicationPlace(
    /**
     * Whether a claim naming this place has its value carried there, rather than only its name said to
     * exist.
     */
    val carriesAValue: Boolean = true
) {
    /**
     * The id token issued at the end of an authorization, and reissued by a refresh.
     */
    ID_TOKEN,

    /**
     * The `/userinfo` response.
     */
    USERINFO,

    /**
     * The JWT access token, where RFC 9068 §2.2.3 defines the profile that carries identity claims.
     *
     * It is the one place a value travels as a bearer credential presented on every request, for the
     * whole life of the token, to every resource server of its audience — so withdrawing a claim from it
     * means revoking the tokens that already carry one.
     */
    ACCESS_TOKEN,

    /**
     * The introspection response, as a top-level member, which RFC 7662 §2.2 leaves open to one and
     * RFC 9470 §6.2 puts `auth_time` in.
     */
    INTROSPECTION,

    /**
     * `claims_supported` in the discovery document, which says the claim exists and never what its value
     * is.
     */
    DISCOVERY(carriesAValue = false)
}

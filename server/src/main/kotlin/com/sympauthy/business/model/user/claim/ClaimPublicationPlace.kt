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
 *
 * Two of them a client may ask a claim into, which is what [nameableInAClaimsRequest] separates. A
 * claim is carried there on the strength of the deployment having opened the channel to a request —
 * `claims.<id>.published-in-when-requested` — and never on the strength of the request alone.
 */
enum class ClaimPublicationPlace(
    /**
     * Whether a claim naming this place has its value carried there, rather than only its name said to
     * exist.
     */
    val carriesAValue: Boolean = true,
    /**
     * Whether a client may name this place in the `claims` request parameter, which is true of the two
     * OpenID channels OpenID Connect Core §5.5 defines a member for — `id_token` and `userinfo` — and of
     * no other place.
     *
     * A place this is false of is one no request can reach, so naming it in
     * `claims.<id>.published-in-when-requested` is a setting that could never take effect and
     * [com.sympauthy.config.validation.ClaimsConfigValidator] refuses it at startup.
     * [RequestedClaims.namesIn] is what a request answers with for each of them.
     */
    val nameableInAClaimsRequest: Boolean = false
) {
    /**
     * The id token issued at the end of an authorization, and reissued by a refresh.
     */
    ID_TOKEN(nameableInAClaimsRequest = true),

    /**
     * The `/userinfo` response.
     */
    USERINFO(nameableInAClaimsRequest = true),

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

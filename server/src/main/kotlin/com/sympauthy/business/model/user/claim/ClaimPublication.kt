package com.sympauthy.business.model.user.claim

/**
 * An OpenID channel a claim's value travels through.
 *
 * It answers where a value goes and not who may know it: what a caller is entitled to is the
 * claim's [ClaimAcl], and the channels a claim names only narrow what that already permits. The
 * access token is deliberately not one of these — it carries no claim about a person. The
 * `auth_time` it does carry is a property of the authentication behind the token rather than
 * something this server was told about anybody, so no claim declares it and nothing filters it.
 */
enum class ClaimPublication {
    /**
     * The id token issued at the end of an authorization, and reissued by a refresh.
     */
    ID_TOKEN,

    /**
     * The `/userinfo` response.
     */
    USERINFO
}

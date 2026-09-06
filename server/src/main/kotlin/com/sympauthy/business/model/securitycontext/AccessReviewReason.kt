package com.sympauthy.business.model.securitycontext

/**
 * What a client is being asked to review: the request of its that is validating a token.
 *
 * These are the two places a token is presented long after it was issued, which is what makes them
 * worth reviewing where the sign-in itself is not.
 */
enum class AccessReviewReason {
    /** The OpenID Connect UserInfo endpoint. */
    USERINFO,

    /** The OAuth2 token endpoint under the `refresh_token` grant. */
    REFRESH_TOKEN
}

package com.sympauthy.business.model.token

/**
 * The tokens a grant issued in answer to one request to the token endpoint.
 */
data class IssuedTokens(
    val accessToken: EncodedAuthenticationToken,
    /**
     * Null where the grant issued none.
     */
    val refreshToken: EncodedAuthenticationToken? = null,
    /**
     * Null where the grant issued none.
     */
    val idToken: EncodedAuthenticationToken? = null
)

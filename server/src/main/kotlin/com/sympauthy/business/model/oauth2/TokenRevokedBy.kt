package com.sympauthy.business.model.oauth2

enum class TokenRevokedBy {
    /** The token was revoked by the client via the RFC 7009 revocation endpoint. */
    CLIENT,

    /** The token was revoked by an administrator via the admin API. */
    ADMIN,

    /** The token was revoked as a consequence of the user's consent being revoked. */
    CONSENT_REVOKED,

    /**
     * The token was revoked because the authorization code its grant came from was presented again
     * after it had been exchanged, RFC 6749 §4.1.2.
     */
    AUTHORIZATION_CODE_REPLAYED
}

package com.sympauthy.business.model.oauth2

enum class TokenRevokedBy {
    /** The token was revoked by the client via the RFC 7009 revocation endpoint. */
    CLIENT,
    /** The token was revoked by an administrator via the admin API. */
    ADMIN,
    /** The token was revoked as a consequence of the user's consent being revoked. */
    CONSENT_REVOKED
}

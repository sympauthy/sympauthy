package com.sympauthy.business.model.grant

enum class GrantRevokedBy {
    /** The grant was revoked by its client via the RFC 7009 revocation endpoint. */
    CLIENT,

    /** The grant was revoked by an administrator via the admin API. */
    ADMIN,

    /** The grant was revoked as a consequence of the user's consent being revoked. */
    CONSENT_REVOKED,

    /**
     * The grant was revoked because the authorization code issued under it was presented again after it had
     * been exchanged, RFC 6749 §4.1.2.
     */
    AUTHORIZATION_CODE_REPLAYED
}

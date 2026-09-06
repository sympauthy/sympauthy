package com.sympauthy.business.model.securitycontext

/**
 * What a client's access-review webhook answered about the place a request was made from.
 *
 * This server scores nothing and decides nothing here: it records where somebody signed in from,
 * hands that to the client, and applies what comes back.
 */
enum class AccessReviewDecision {
    /** The request goes through, and the place is remembered as one this client has allowed. */
    ALLOW,

    /** The request is refused. Nothing is revoked, and the same place is asked about again. */
    DENY,

    /** The request is refused, and every token issued to the sign-in it belongs to is revoked. */
    REVOKE_SESSION
}

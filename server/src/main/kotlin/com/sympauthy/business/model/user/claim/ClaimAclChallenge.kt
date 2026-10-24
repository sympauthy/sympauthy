package com.sympauthy.business.model.user.claim

import java.time.Duration

/**
 * What a client is told to do about a write a claim's ACL refused: a condition of the expression that a
 * different credential would have met and the one presented did not.
 *
 * Each is a challenge of its own on the wire, and a refusal carries every one of them, so a client reads
 * whichever it understands, re-authorizes, and retries. A condition no credential could be re-authorized
 * into — a scope a rule grants, the client, a literal — carries none.
 */
sealed class ClaimAclChallenge {

    /**
     * The person has not consented to [scopes], which RFC 6750 §3 answers with `insufficient_scope` naming
     * all of them in its `scope`.
     */
    data class InsufficientScope(val scopes: List<String>) : ClaimAclChallenge()

    /**
     * The person authenticated longer ago than [maxAge] allows, or no person authenticated at all, which
     * RFC 9470 §3 answers with `insufficient_user_authentication` and `max_age`.
     */
    data class InsufficientUserAuthentication(val maxAge: Duration) : ClaimAclChallenge()

    /**
     * The token is not bound to a key, which RFC 9449 §7.1 answers with a `DPoP` challenge.
     */
    data object UnboundToken : ClaimAclChallenge()
}

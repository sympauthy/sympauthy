package com.sympauthy.business.model.oauth2

import java.time.Duration

/**
 * A challenge this server answers a refused token with, in the `WWW-Authenticate` header of a `401`: what
 * the client can do so that a token it presents next is accepted.
 *
 * A refusal may carry several, RFC 9110 §11.6.1 allowing it, so a client reads whichever it understands,
 * re-authorizes, and retries. A refusal no token could be re-authorized out of — a scope a rule grants, the
 * client, a literal — carries none.
 */
sealed class WwwAuthenticateChallenge {

    /**
     * The person has not consented to [scopes], which RFC 6750 §3 answers with `insufficient_scope` naming
     * all of them in its `scope`.
     */
    data class InsufficientScope(val scopes: List<String>) : WwwAuthenticateChallenge()

    /**
     * The person authenticated longer ago than [maxAge] allows, or no person authenticated at all, which
     * RFC 9470 §3 answers with `insufficient_user_authentication` and `max_age`.
     */
    data class InsufficientUserAuthentication(val maxAge: Duration) : WwwAuthenticateChallenge()

    /**
     * The token is not bound to a key, which RFC 9449 §7.1 answers with a `DPoP` challenge.
     */
    data object UnboundToken : WwwAuthenticateChallenge()
}

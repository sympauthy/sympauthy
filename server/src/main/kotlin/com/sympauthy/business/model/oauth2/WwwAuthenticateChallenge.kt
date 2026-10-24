package com.sympauthy.business.model.oauth2

import java.time.Duration

/**
 * A challenge this server answers a refused access token with, in the `WWW-Authenticate` header: what the
 * client can do so that a token it presents next is accepted.
 *
 * A refusal may carry several, RFC 9110 §11.6.1 allowing it, so a client reads whichever it understands,
 * re-authorizes, and retries. A refusal no token could be re-authorized out of — a scope a rule grants, the
 * client, a literal — carries none. How a list of them is written into one response, and in which order,
 * is `docs/design/security.md`'s.
 */
sealed class WwwAuthenticateChallenge {

    /**
     * The token does not validate — expired, revoked, malformed, or standing under a grant that is no longer
     * honoured — which RFC 6750 §3.1 answers with `invalid_token`.
     */
    data object InvalidToken : WwwAuthenticateChallenge()

    /**
     * The DPoP proof presented beside the token does not validate, which RFC 9449 §7.1 answers with
     * `invalid_dpop_proof`.
     */
    data object InvalidDpopProof : WwwAuthenticateChallenge()

    /**
     * The operation needs [scopes] and the token was not issued with them, which RFC 6750 §3.1 answers with
     * `insufficient_scope` naming them in its `scope`.
     *
     * A refusal that knows which scopes the token lacks challenges only the ones a client obtains by
     * re-authorizing, which the person consents to. A scope only a rule grants is refused by name instead,
     * since no authorization the client asks for can bring it.
     */
    data class InsufficientScope(
        /**
         * The scopes the person has to consent to, empty where the refusal is not told which: the framework
         * turning a request away on a route's gate is not told which rule it failed.
         */
        val scopes: List<String>
    ) : WwwAuthenticateChallenge()

    /**
     * The person authenticated longer ago than [maxAge] allows, or no person authenticated at all, which
     * RFC 9470 §3 answers with `insufficient_user_authentication` and `max_age`.
     */
    data class InsufficientUserAuthentication(val maxAge: Duration) : WwwAuthenticateChallenge()

    /**
     * The token is not bound to a key and the operation asks for one. RFC 9449 defines no error for it, and
     * it is answered with `invalid_token` on the `DPoP` challenge, which its §7.1 answers a binding that
     * does not hold with.
     */
    data object UnboundToken : WwwAuthenticateChallenge()
}

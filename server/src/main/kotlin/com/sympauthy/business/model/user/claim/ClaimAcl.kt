package com.sympauthy.business.model.user.claim

import java.time.Duration

/**
 * Resolved access control list for a claim: one door per party and direction, each opened by an
 * expression a deployment wrote, or shut where it wrote none. No door covers another; see
 * `docs/design/claims.md`. `ClaimAclManager` is where each is evaluated against a credential.
 */
data class ClaimAcl(
    /**
     * The door the interactive flow collects this claim through, in both directions — what the flow offers
     * and what it reads back. It has no token behind it, so its expression may ask about consent and the
     * client and nothing else.
     *
     * **An identifier claim is outside it**: `ConsentAwareCollectedClaimManager` takes one whatever this
     * says, so opening this door on one changes nothing the flow does.
     *
     * **It is accepted on an identifier claim rather than refused**: the shipped claims open it, and the
     * shipped `mail` environment identifies by `email`. A refusal on the resolved ACL — where the other
     * cross-checks of this kind deliberately look — would reject a file shipped with the server on a value
     * no deployment wrote.
     */
    val collectedInFlow: ClaimAclExpression?,
    val readableByPerson: ClaimAclExpression?,
    /**
     * The door a person's own access token writes this claim through, which is a different permission from
     * [collectedInFlow] and shut unless a deployment opens it. See `docs/design/claims.md`.
     */
    val writableByPerson: ClaimAclExpression?,
    val readableByClient: ClaimAclExpression?,
    val writableByClient: ClaimAclExpression?
) {

    /**
     * Every scope a door of this claim opens on by consent, which is what the scope a person agrees to
     * unlocks of it.
     */
    val consentedScopes: Set<String> =
        listOfNotNull(collectedInFlow, readableByPerson, writableByPerson, readableByClient, writableByClient)
            .flatMapTo(mutableSetOf(), ClaimAclExpression::consentedScopes)

    companion object {
        /** An ACL opening no door. */
        val SHUT = ClaimAcl(null, null, null, null, null)
    }
}

/**
 * The expression opening one door of a claim's ACL, with what it names read once when the configuration
 * was.
 *
 * What it names is known before any request, since a scope or a duration is written as a literal; see
 * `docs/standards/expression-standard.md` and [com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge].
 *
 * Each condition held here is one meeting which can open the door: a call nothing but `&&` and `||` stands
 * above in the expression. A call under `!`, a comparison or a function of its own is evaluated like any
 * other and held nowhere here, since meeting it may shut the door rather than open it.
 */
data class ClaimAclExpression(
    val expression: String,
    /**
     * The scopes consent to which can open the door.
     */
    val consentedScopes: Set<String> = emptySet(),
    /**
     * Each age an authentication younger than can open the door.
     */
    val authenticationAges: Set<Duration> = emptySet(),
    /**
     * Whether a token bound to a key can open the door.
     */
    val asksForBoundToken: Boolean = false
)

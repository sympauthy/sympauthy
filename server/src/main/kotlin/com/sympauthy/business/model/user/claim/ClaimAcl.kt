package com.sympauthy.business.model.user.claim

import java.time.Duration

/**
 * Resolved access control list for a claim: one door per party and direction, each opened by an
 * expression a deployment wrote, or shut where it wrote none.
 *
 * A door is named for itself and no door covers another, so the word *person* on a door always means the
 * person holding their own access token and the flow is never one of them. `docs/design/claims.md` is
 * where the doors are told apart, and `ClaimAclManager` is where each is evaluated against a credential.
 */
data class ClaimAcl(
    /**
     * The door the interactive flow collects this claim through, where the person is on this server's own
     * pages having just authenticated. It governs that one door in both directions — what the flow offers
     * and what it reads back — and is neither [readableByPerson], which `/userinfo` answers from, nor
     * [writableByPerson], which a person's own access token writes through.
     *
     * It has no token behind it, so its expression may ask about consent and the client and nothing else.
     *
     * **An identifier claim is outside it.** What an account signs in with is collected by the sign-up
     * rather than by the claims step, and is required of every account by being what it signs in with, so
     * the flow answers for one before this door is reached — the set it offers, the set it holds a person to
     * and the values it reads back each take an identifier claim whatever this says.
     * `ConsentAwareCollectedClaimManager` is where all three are, and opening this door on one changes
     * nothing the flow does.
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
     * [collectedInFlow] and shut unless a deployment opens it.
     *
     * [collectedInFlow] is what the interactive flow collects, where the person is on this server's own
     * pages having just authenticated. A bearer access token says only that a client was authorized to
     * act within some scopes, for as long as a refresh token lets it, so honouring the flow's door here
     * would make every profile claim writable through an API the day a deployment upgraded, with nobody
     * asked.
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
 * What it names is known before any request: a scope or a duration is written as a literal, so the scopes
 * a door opens on are listed by the administration API, and a write the expression refuses is answered
 * with a challenge for each condition a different token would have met — see [ClaimAclChallenge].
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

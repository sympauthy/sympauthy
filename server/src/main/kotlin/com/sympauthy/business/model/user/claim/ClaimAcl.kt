package com.sympauthy.business.model.user.claim

import java.time.Duration

/**
 * Resolved access control list for a claim.
 *
 * Controls who can read/write the claim and under what conditions.
 */
data class ClaimAcl(
    val consent: ConsentAcl,
    val unconditional: UnconditionalAcl
)

/**
 * Consent-gated access control.
 *
 * Access is granted when the relevant boolean flag is true AND (no scope is set OR
 * the end-user has consented to the scope).
 */
data class ConsentAcl(
    /**
     * Scope ID gating consent-based access. Null means no scope prerequisite.
     */
    val scope: String?,
    val readableByPerson: Boolean,
    val collectedInFlow: Boolean,
    /**
     * Whether a person's own access token may write this claim, which is a different permission from
     * [collectedInFlow] and off unless a deployment marks it.
     *
     * [collectedInFlow] is what the interactive flow collects, where the person is on this server's own
     * pages having just authenticated. A bearer access token says only that a client was authorized to
     * act within some scopes, for as long as a refresh token lets it, so honouring the flow's flag here
     * would make every profile claim writable through an API the day a deployment upgraded, with nobody
     * asked. `docs/design/claims.md` is where the two doors are told apart.
     */
    val writableByPerson: Boolean,
    val readableByClient: Boolean,
    val writableByClient: Boolean,
    /**
     * How old the person's authentication may be behind a write through their own access token, or null
     * where the claim asks nothing of it.
     *
     * Set, a write presented with an older authentication is refused with the challenge RFC 9470 defines
     * rather than silently accepted, and this is the `max_age` that challenge carries. It qualifies
     * [writableByPerson] and nothing else: a read is never challenged, and the flow's own write is
     * presence by definition.
     */
    val writeMaxAuthenticationAge: Duration?
)

/**
 * Unconditional access control.
 *
 * Access is granted when the client holds any of the listed client scopes,
 * regardless of end-user consent.
 */
data class UnconditionalAcl(
    val readableWithClientScopes: List<String>,
    val writableWithClientScopes: List<String>
)

package com.sympauthy.business.model.user.claim

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
    val readableByUser: Boolean,
    val writableByUser: Boolean,
    val readableByClient: Boolean,
    val writableByClient: Boolean
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

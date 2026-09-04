package com.sympauthy.business.model.user

/**
 * A user, and every claim value a caller listing them may publish.
 *
 * The values come from two places — [collectedClaims] is what was collected from the user, and
 * [generatedClaimValues] what this server computes for them, keyed by claim identifier — and they
 * are read together so that nothing computes one claim of one row at a time.
 */
data class UserWithClaims(
    val user: User,
    val collectedClaims: List<CollectedClaim>,
    val generatedClaimValues: Map<String, Any?>
)

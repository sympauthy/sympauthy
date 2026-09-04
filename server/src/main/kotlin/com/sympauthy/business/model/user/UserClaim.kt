package com.sympauthy.business.model.user

import com.sympauthy.business.model.user.claim.Claim

/**
 * A claim of this authorization server, seen through one user.
 *
 * It has two shapes, and which one a claim takes is [Claim.generated]: a value this server computes
 * for the user, or a value collected from them.
 */
sealed interface UserClaim {
    val claim: Claim

    /**
     * Whether this claim is one of the claims a user is identified by.
     */
    val identifier: Boolean
}

/**
 * A claim whose value this server computes for the user rather than collecting it.
 */
data class GeneratedUserClaim(
    override val claim: Claim,
    override val identifier: Boolean,
    val value: Any?
) : UserClaim

/**
 * A claim collected from the user, or one nothing has been collected for yet, which is what a null
 * [collectedClaim] says.
 */
data class CollectedUserClaim(
    override val claim: Claim,
    override val identifier: Boolean,
    val collectedClaim: CollectedClaim?
) : UserClaim

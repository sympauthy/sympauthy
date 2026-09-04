package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.filter.matches
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.page.orderedPage
import com.sympauthy.business.model.user.claim.ClaimOrigin
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * Manager responsible for filtering the claims of one user, each with the value that user holds
 * for it.
 *
 * A row is a claim this deployment serves rather than a value it collected, so a claim the user
 * never gave a value for is listed too. The claim carrying whether another one was verified is not:
 * what it says is published on the claim it verifies.
 */
@Singleton
class UserClaimSearchManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Read the page [pageParams] names of the claims of the user [userId] the criteria keep, ordered
     * by claim identifier.
     *
     * Every criterion is optional and they compose. [claimId], [identifier], [required] and
     * [origin] are the claim's own, and an [origin] naming no [ClaimOrigin] keeps nothing rather
     * than everything. [collected] is whether the user has a value for the claim and [verified]
     * whether this server verified it, both read from what was collected from that user.
     */
    suspend fun listUserClaims(
        userId: UUID,
        claimId: String?,
        identifier: Boolean?,
        required: Boolean?,
        collected: Boolean?,
        verified: Boolean?,
        origin: ValueFilter<ClaimOrigin>,
        pageParams: PageParams
    ): Page<UserClaim> {
        val identifierClaimIds = uncheckedAuthConfig.orThrow()
            .identifierClaims
            .toSet()
        val enabledClaims = claimManager.listEnabledClaims()
        val verifiedClaimIds = enabledClaims.mapNotNull { it.verifiedId }.toSet()

        val claims = enabledClaims
            .filter { it.id !in verifiedClaimIds }
            .filter { claimId == null || it.id == claimId }
            .filter { identifier == null || (it.id in identifierClaimIds) == identifier }
            .filter { required == null || it.required == required }
            .filter { origin.matches(it.origin) }

        // Only the claims the criteria above kept are worth reading a value for.
        val collectedClaims = collectedClaimManager.findByUserIdAndClaims(userId, claims)
            .associateBy { it.claim.id }
        val remainingClaims = claims
            .filter { collected == null || (collectedClaims[it.id]?.value != null) == collected }
            .filter { verified == null || (collectedClaims[it.id]?.verificationDate != null) == verified }

        val generatedClaimValues = generatedClaimsManager.computeValues(userId)
        return remainingClaims
            .orderedPage(pageParams, compareBy { it.id })
            .map { claim ->
                val isIdentifier = claim.id in identifierClaimIds
                if (claim.generated) {
                    GeneratedUserClaim(claim, isIdentifier, generatedClaimValues[claim.id])
                } else {
                    CollectedUserClaim(claim, isIdentifier, collectedClaims[claim.id])
                }
            }
    }

    /**
     * A claim of this authorization server, seen through one user.
     *
     * It has two shapes, and which one a claim takes is [Claim.generated]: a value this server
     * computes for the user, or a value collected from them.
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
     * A claim collected from the user, or one nothing has been collected for yet, which is what a
     * null [collectedClaim] says.
     */
    data class CollectedUserClaim(
        override val claim: Claim,
        override val identifier: Boolean,
        val collectedClaim: CollectedClaim?
    ) : UserClaim
}

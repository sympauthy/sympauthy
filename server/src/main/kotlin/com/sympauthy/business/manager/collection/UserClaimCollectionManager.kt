package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.BOOLEAN
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
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
class UserClaimCollectionManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of the claims of the user [userId] that [criteria] keep.
     *
     * The value of every listable claim is read before the criteria run, because a criterion may be
     * about the value: `collected`, `verified` and `value` are all read off what was collected from
     * that user, and the read is one query whichever of them the caller named.
     */
    suspend fun listUserClaims(
        userId: UUID,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<UserClaim> {
        val identifierClaimIds = uncheckedAuthConfig.orThrow().identifierClaims.toSet()
        val claims = listableClaims()
        val collectedClaims = collectedClaimManager.findByUserIdAndClaims(userId, claims)
            .associateBy { it.claim.id }
        val generatedClaimValues = generatedClaimsManager.computeValues(userId)

        val userClaims = claims.map { claim ->
            val isIdentifier = claim.id in identifierClaimIds
            if (claim.generated) {
                GeneratedUserClaim(claim, isIdentifier, generatedClaimValues[claim.id])
            } else {
                CollectedUserClaim(claim, isIdentifier, collectedClaims[claim.id])
            }
        }
        return fields().page(userClaims, criteria, pageParams)
    }

    /**
     * The claims this collection has a row for: the ones this deployment serves, less the ones whose
     * whole job is to say that another claim was verified.
     */
    private fun listableClaims(): List<Claim> {
        val enabledClaims = claimManager.listEnabledClaims()
        val verifiedClaimIds = enabledClaims.mapNotNull(Claim::verifiedId).toSet()
        return enabledClaims.filter { it.id !in verifiedClaimIds }
    }

    /**
     * The fields this collection offers, ending on the claim identifier, which is unique by
     * construction — and which is therefore not one a caller may order by.
     *
     * `collected` and `verified` are read off what was collected from the user, so a claim whose
     * value this server computes is neither: nothing was collected for it, and the value beside it
     * is this server's own.
     */
    private suspend fun fields(): CollectionFields<UserClaim> = collectionFields(
        uniqueKey = compareBy { it.claim.id }
    ) {
        field(
            name = "claim_id",
            type = ENUM,
            key = "fields.claim_id",
            values = listableClaims().map { CollectionFieldValue(it.id, "claims.${it.id}") },
            searchable = true
        ) { it.claim.id }
        field("identifier", BOOLEAN, sortable = true, read = UserClaim::identifier)
        field("required", BOOLEAN, sortable = true) { it.claim.required }
        field("generated", BOOLEAN, sortable = true) { it.claim.generated }
        enumeration<ClaimOrigin>("origin", key = "fields.claim_origin", sortable = true) { it.claim.origin }
        enumeration<ClaimDataType>("data_type", key = "fields.claim_data_type", sortable = true) {
            it.claim.dataType
        }
        field("collected", BOOLEAN, sortable = true) { it.collectedClaim?.value != null }
        field("verified", BOOLEAN, sortable = true) { it.collectedClaim?.verificationDate != null }
        field("value", STRING, nullable = true, searchable = true) { it.value }
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

        /**
         * The value the user holds for this claim, wherever it came from, or null where they hold
         * none.
         */
        val value: Any?

        /**
         * What was collected from the user for this claim, or null where this server computed the
         * value instead or nothing has been collected yet.
         */
        val collectedClaim: CollectedClaim?
    }

    /**
     * A claim whose value this server computes for the user rather than collecting it.
     */
    data class GeneratedUserClaim(
        override val claim: Claim,
        override val identifier: Boolean,
        override val value: Any?
    ) : UserClaim {
        override val collectedClaim: CollectedClaim? get() = null
    }

    /**
     * A claim collected from the user, or one nothing has been collected for yet, which is what a
     * null [collectedClaim] says.
     */
    data class CollectedUserClaim(
        override val claim: Claim,
        override val identifier: Boolean,
        override val collectedClaim: CollectedClaim?
    ) : UserClaim {
        override val value: Any? get() = collectedClaim?.value
    }
}

package com.sympauthy.business.collection.manager

import com.sympauthy.business.claim.manager.ClaimManager
import com.sympauthy.business.claim.manager.CollectedClaimManager
import com.sympauthy.business.claim.manager.GeneratedClaimsManager
import com.sympauthy.business.claim.model.Claim
import com.sympauthy.business.claim.model.ClaimDataType
import com.sympauthy.business.claim.model.ClaimGroup
import com.sympauthy.business.claim.model.ClaimKind
import com.sympauthy.business.claim.model.ClaimOrigin
import com.sympauthy.business.claim.model.CollectedClaim
import com.sympauthy.business.claim.model.EnabledClaim
import com.sympauthy.business.claim.model.GeneratedOpenIdConnectClaim
import com.sympauthy.business.collection.model.CollectionCapabilities
import com.sympauthy.business.collection.model.CollectionCriteria
import com.sympauthy.business.collection.model.CollectionFieldType.BOOLEAN
import com.sympauthy.business.collection.model.CollectionFieldType.ENUM
import com.sympauthy.business.collection.model.CollectionFieldType.STRING
import com.sympauthy.business.collection.model.CollectionFieldValue
import com.sympauthy.business.collection.model.CollectionFields
import com.sympauthy.business.collection.model.Page
import com.sympauthy.business.collection.model.PageParams
import com.sympauthy.business.collection.model.collectionFields
import com.sympauthy.business.user.model.User
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
     * Read the page [pageParams] names of the claims of the [user] that [criteria] keep.
     *
     * The value of every listable claim is read before the criteria run, because a criterion may be
     * about the value: `collected`, `verified` and `value` are all read off what was collected from
     * that user, and the read is one query whichever of them the caller named.
     */
    suspend fun listUserClaims(
        user: User,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<UserClaim> {
        val identifierClaimIds = uncheckedAuthConfig.orThrow().identifierClaims.toSet()
        val claims = listableClaims()
        val collectedClaims = collectedClaimManager.findByUserIdAndClaims(user.id, claims)
            .associateBy { it.claim.id }
        val generatedClaimValues = generatedClaimsManager.computeValues(user)

        val userClaims = claims.map { claim ->
            val isIdentifier = claim.id in identifierClaimIds
            if (claim.generated) {
                GeneratedUserClaim(claim, isIdentifier, generatedClaimValues[claim.id])
            } else {
                CollectedUserClaim(claim, isIdentifier, collectedClaims[claim.id])
            }
        }
        return fieldsOf(claims).page(userClaims, criteria, pageParams)
    }

    /**
     * The claims this collection has a row for: the ones this deployment serves, less the ones whose
     * whole job is to say that another claim was verified, and less the generated ones that describe
     * an authorization rather than an account.
     *
     * The last of those is what keeps `auth_time` off a person's row. This collection answers what
     * this server knows about somebody, and an authentication time is not known about a person —
     * see [GeneratedOpenIdConnectClaim.computedPerAccount].
     */
    private fun listableClaims(): List<EnabledClaim> {
        val enabledClaims = claimManager.listEnabledClaims()
        val verifiedClaimIds = enabledClaims.mapNotNull(Claim::verifiedId).toSet()
        return enabledClaims
            .filter { it.id !in verifiedClaimIds }
            .filter { !it.generated || it.id in GeneratedOpenIdConnectClaim.idsComputedPerAccount }
    }

    /**
     * The fields this collection offers, ending on the claim identifier, which is unique by
     * construction — and which is therefore not one a caller may order by.
     *
     * `collected` and `verified` are read off what was collected from the user, so a claim whose
     * value this server computes is neither: nothing was collected for it, and the value beside it
     * is this server's own.
     */
    private suspend fun fields(): CollectionFields<UserClaim> = fieldsOf(listableClaims())

    /**
     * The fields this collection offers over [claims], which the caller has already read: one pass
     * over the configured set answers both what the collection accepts and what it reads.
     */
    private suspend fun fieldsOf(claims: List<EnabledClaim>): CollectionFields<UserClaim> = collectionFields(
        uniqueKey = compareBy { it.claim.id }
    ) {
        field(
            name = "claim_id",
            type = ENUM,
            key = "fields.claim_id",
            values = claims.map { CollectionFieldValue(it.id, "claims.${it.id}") },
            searchable = true
        ) { it.claim.id }
        field("identifier", BOOLEAN, sortable = true, read = UserClaim::identifier)
        field("required", BOOLEAN, sortable = true) { it.claim.required }
        field("generated", BOOLEAN, sortable = true) { it.claim.generated }
        enumeration<ClaimOrigin>("origin", key = "fields.claim_origin", sortable = true) { it.claim.origin }
        enumeration<ClaimKind>("kind", key = "fields.claim_kind", nullable = true, sortable = true) { it.claim.kind }
        enumeration<ClaimDataType>("data_type", key = "fields.claim_data_type", sortable = true) {
            it.claim.dataType
        }
        enumeration<ClaimGroup>("group", key = "fields.claim_group", nullable = true, sortable = true) {
            it.claim.group
        }
        field("collected", BOOLEAN, sortable = true) { it.collectedClaim?.value != null }
        field("verified", BOOLEAN, sortable = true) { it.collectedClaim?.verificationDate != null }
        // Read where its claim's type is a field a collection offers, and as no value elsewhere: a document is
        // neither searched nor filtered on, see [asFieldTypeOrNull].
        field("value", STRING, nullable = true, searchable = true) { userClaim ->
            userClaim.value.takeIf { userClaim.claim.dataType.asFieldTypeOrNull != null }
        }
    }

    /**
     * A claim of this authorization server, seen through one user.
     *
     * It has two shapes, and which one a claim takes is [Claim.generated]: a value this server
     * computes for the user, or a value collected from them.
     */
    sealed interface UserClaim {
        val claim: EnabledClaim

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
        override val claim: EnabledClaim,
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
        override val claim: EnabledClaim,
        override val identifier: Boolean,
        override val collectedClaim: CollectedClaim?
    ) : UserClaim {
        override val value: Any? get() = collectedClaim?.value
    }
}

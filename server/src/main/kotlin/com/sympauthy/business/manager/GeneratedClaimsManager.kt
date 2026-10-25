package com.sympauthy.business.manager

import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.data.repository.CollectedClaimRepository
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Computes values for generated claims — claims whose values are produced by the authorization server
 * at runtime rather than collected from users (e.g. `sub`, `updated_at`).
 *
 * It answers for an account, so it reads the generated claims that have a value for one. A generated
 * claim belonging to a single authorization is left out: see
 * [GeneratedOpenIdConnectClaim.computedPerAccount].
 */
@Singleton
class GeneratedClaimsManager(
    private val claimManager: ClaimManager,
    private val claimAclManager: ClaimAclManager,
    private val collectedClaimRepository: CollectedClaimRepository
) {

    /**
     * Compute values for all enabled generated claims for the given [user].
     */
    suspend fun computeValues(user: User): Map<String, Any?> = computeValues(user.id, { true }) {
        computeUpdatedAt(user.id)
    }

    /**
     * Compute values for the enabled generated claims of the [user] that a client may read in [context]:
     * those whose client read door is open, which is the door every claim a client reads is asked of.
     */
    suspend fun computeValuesReadableByClient(user: User, context: ClaimAclContext): Map<String, Any?> {
        val doors = claimAclManager.getTokenDoors(context)
        return computeValues(user.id, doors::canBeReadByClient) { computeUpdatedAt(user.id) }
    }

    /**
     * Compute values for all enabled generated claims of the [user], reading `updated_at`
     * from the date the caller already holds instead of querying for it.
     *
     * [latestCollectionDate] is the date of the most recent claim collected from that user, over
     * every row the table holds for them. A date read from a subset — the claims of one page, or
     * only the rows whose claim is still configured — answers with a date that is too old, and one
     * this server publishes elsewhere from the full table would then disagree with it.
     */
    suspend fun computeValues(user: User, latestCollectionDate: LocalDateTime?): Map<String, Any?> =
        computeValues(user.id, { true }) { latestCollectionDate?.epochSecond }

    private suspend fun computeValues(
        userId: UUID,
        readable: (EnabledClaim) -> Boolean,
        updatedAt: suspend () -> Long?
    ): Map<String, Any?> {
        // A generated claim this server computes per authorization rather than per account — `auth_time`
        // — has no value to answer here, and answering null for it would publish it against everybody.
        val generatedClaims = claimManager.listEnabledClaims()
            .filter { it.id in GeneratedOpenIdConnectClaim.idsComputedPerAccount && readable(it) }
        return generatedClaims.associate { claim ->
            claim.id to when (claim.id) {
                OpenIdConnectClaimId.SUB -> computeSubject(userId)
                OpenIdConnectClaimId.UPDATED_AT -> updatedAt()
                else -> null
            }
        }
    }

    /**
     * Compute the value of the `sub` claim for the given user.
     */
    fun computeSubject(userId: UUID): String = userId.toString()

    /**
     * Compute the value of the `updated_at` claim for the user [userId].
     * Returns the timestamp (as Unix epoch seconds) of the most recent claim collection, or null if no claims
     * have been collected.
     *
     * It takes an identifier rather than a user: its caller outside this manager is `/userinfo`, a protocol
     * endpoint, which answers from the validated token without resolving the account it names.
     */
    suspend fun computeUpdatedAt(userId: UUID): Long? =
        collectedClaimRepository.findMaxCollectionDateByUserId(userId)?.epochSecond

    private val LocalDateTime.epochSecond: Long get() = toInstant(ZoneOffset.UTC).epochSecond
}

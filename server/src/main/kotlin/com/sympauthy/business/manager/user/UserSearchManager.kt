package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.UserWithClaims
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList

/**
 * Manager responsible for searching, filtering, and sorting users along with their collected claims.
 *
 * All filtering, text search, and sorting operations are performed in-memory rather than in the database.
 * This design choice is driven by the fact that claim values are stored in a separate table (collected_claims)
 * with a generic key-value structure, making SQL-based cross-claim filtering and sorting impractical — especially
 * across different database engines (H2 and PostgreSQL). This approach is consistent with the pattern used by
 * other admin endpoints (ex. [com.sympauthy.api.controller.admin.AdminClaimController]).
 *
 * This design should remain sustainable up to thousands of users, which is beyond the intended scale for SympAuthy.
 */
@Singleton
class UserSearchManager(
    @Inject private val userRepository: UserRepository,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val claimManager: ClaimManager,
    @Inject private val claimValueValidator: ClaimValueValidator,
    @Inject private val userMapper: UserMapper,
    @Inject private val collectedClaimMapper: CollectedClaimMapper
) {

    /**
     * Search and filter users with their claims.
     *
     * Every criterion is optional and they compose: [status] keeps the users in one [UserStatus], [query] is a
     * partial case-insensitive match across the values of every enabled claim, and [claimFilters] are
     * exact-match values keyed by claim id.
     *
     * The result carries no order of its own — [getUserComparator] is what puts it in one.
     *
     * A criterion naming something that does not exist is the caller's mistake rather than an empty result: an
     * unknown claim id or status each throw a recoverable business exception carrying
     * `user.search.invalid_claim` or `user.search.invalid_status`.
     */
    suspend fun listUsers(
        status: String?,
        query: String?,
        claimFilters: Map<String, String>
    ): List<UserWithClaims> {
        val enabledClaims = claimManager.listEnabledClaims()

        // Validate claim filter keys and deserialize filter values
        val enabledClaimMap = enabledClaims.associateBy { it.id }
        val deserializedFilters = claimFilters.map { (claimId, rawValue) ->
            val claim = enabledClaimMap[claimId] ?: throw recoverableBusinessExceptionOf(
                "user.search.invalid_claim",
                "description.user.search.invalid_claim",
                "claim" to claimId
            )
            val value = claimValueValidator.validateAndCleanValueForClaim(claim, rawValue)
                .orElse(null)
            claimId to value
        }

        // Validate status
        val resolvedStatus = status?.let {
            try {
                UserStatus.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                throw recoverableBusinessExceptionOf(
                    "user.search.invalid_status",
                    "description.user.search.invalid_status",
                    "status" to it,
                    "supportedValues" to UserStatus.entries.joinToString(", ") { s -> s.name.lowercase() }
                )
            }
        }

        // Load users
        val userEntities = if (resolvedStatus != null) {
            userRepository.findByStatus(resolvedStatus.name).toList()
        } else {
            userRepository.findAll().toList()
        }

        if (userEntities.isEmpty()) {
            return emptyList()
        }

        val users = userEntities.map(userMapper::toUser)
        val userIds = users.map { it.id }

        // Batch-load all claims
        val claimEntities = collectedClaimRepository.findByUserIdInList(userIds)
        val collectedClaims = claimEntities.mapNotNull(collectedClaimMapper::toCollectedClaim)
        val claimsByUserId = collectedClaims.groupBy { it.userId }

        // Build UserWithClaims
        var result = users.map { user ->
            UserWithClaims(
                user = user,
                collectedClaims = claimsByUserId[user.id] ?: emptyList()
            )
        }

        // Apply exact claim filters
        if (deserializedFilters.isNotEmpty()) {
            result = result.filter { uwc ->
                deserializedFilters.all { (claimId, expectedValue) ->
                    uwc.collectedClaims.any { cc ->
                        cc.claim.id == claimId && cc.value == expectedValue
                    }
                }
            }
        }

        // Apply text search
        if (!query.isNullOrBlank()) {
            val lowerQuery = query.lowercase()
            result = result.filter { uwc ->
                uwc.collectedClaims.any { cc ->
                    cc.claim.enabled && cc.value?.toString()?.lowercase()?.contains(lowerQuery) == true
                }
            }
        }

        return result
    }

    /**
     * Build the order a page of [listUsers] results is returned in.
     *
     * [sort] names `created_at`, `status` or a claim id, and [order] is `asc` or `desc`, ascending when it is
     * null. A [sort] naming none of those is the caller's mistake and throws a recoverable business exception
     * carrying `user.search.invalid_sort`.
     *
     * What the caller asked to sort by decides nothing between two users holding the same value, so the order
     * ends in the user's identifier and is total. That tiebreak stays ascending under `order=desc`: it is not
     * part of what the caller asked to sort by, it is there to decide what their own key leaves undecided.
     */
    suspend fun getUserComparator(
        sort: String?,
        order: String?
    ): Comparator<UserWithClaims> {
        val enabledClaimIds = claimManager.listEnabledClaims().map { it.id }.toSet()
        if (sort != null && sort != "created_at" && sort != "status" && sort !in enabledClaimIds) {
            throw recoverableBusinessExceptionOf(
                "user.search.invalid_sort",
                "description.user.search.invalid_sort",
                "property" to sort
            )
        }

        val bySortProperty: Comparator<UserWithClaims> = when (sort) {
            null, "created_at" -> compareBy { it.user.creationDate }
            "status" -> compareBy { it.user.status.name }
            // A claim holds at most one row per user, so the value this reads is the user's own and does not
            // vary between two calls.
            else -> compareBy<UserWithClaims, String?>(nullsLast()) { uwc ->
                uwc.collectedClaims
                    .firstOrNull { it.claim.id == sort }
                    ?.value?.toString()
            }
        }

        val ascending = order?.lowercase() != "desc"
        return (if (ascending) bySortProperty else bySortProperty.reversed())
            .thenBy { it.user.id }
    }

    /**
     * Validate that the given claim IDs reference valid enabled claims.
     * Returns the list of matching [Claim] objects.
     */
    fun validateAndResolveClaimIds(claimIds: List<String>): List<Claim> {
        val enabledClaims = claimManager.listEnabledClaims()
        val enabledClaimMap = enabledClaims.associateBy { it.id }
        return claimIds.map { id ->
            enabledClaimMap[id] ?: throw recoverableBusinessExceptionOf(
                "user.search.invalid_claim",
                "description.user.search.invalid_claim",
                "claim" to id
            )
        }
    }
}

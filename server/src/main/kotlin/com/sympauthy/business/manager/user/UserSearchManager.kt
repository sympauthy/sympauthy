package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.UserWithClaims
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.toList

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
     * Search, filter, and sort users with their claims.
     *
     * @param status Filter by user status.
     * @param query Partial case-insensitive text search across all enabled claim values.
     * @param claimFilters Exact-match filters keyed by claim ID.
     * @param sort Property to sort by: "created_at", "status", or a claim ID.
     * @param order Sort direction: "asc" or "desc". Defaults to "asc".
     */
    suspend fun listUsers(
        status: String?,
        query: String?,
        claimFilters: Map<String, String>,
        sort: String?,
        order: String?
    ): List<UserWithClaims> {
        val enabledClaims = claimManager.listEnabledClaims()
        val enabledClaimIds = enabledClaims.map { it.id }.toSet()

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

        // Validate sort property
        if (sort != null && sort != "created_at" && sort != "status" && sort !in enabledClaimIds) {
            throw recoverableBusinessExceptionOf(
                "user.search.invalid_sort",
                "description.user.search.invalid_sort",
                "property" to sort
            )
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

        // Sort
        val ascending = order?.lowercase() != "desc"
        result = when (sort) {
            null, "created_at" -> {
                if (ascending) result.sortedBy { it.user.creationDate }
                else result.sortedByDescending { it.user.creationDate }
            }
            "status" -> {
                if (ascending) result.sortedBy { it.user.status.name }
                else result.sortedByDescending { it.user.status.name }
            }
            else -> {
                // Sort by claim value
                val comparator = compareBy<UserWithClaims, String?>(nullsLast()) { uwc ->
                    uwc.collectedClaims
                        .firstOrNull { it.claim.id == sort }
                        ?.value?.toString()
                }
                if (ascending) result.sortedWith(comparator)
                else result.sortedWith(comparator.reversed())
            }
        }

        return result
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

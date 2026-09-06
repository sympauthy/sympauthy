package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.SortOrder
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.page.orderedPage
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import kotlinx.coroutines.flow.toList

/**
 * Manager responsible for searching, filtering, and sorting users along with their collected claims.
 *
 * All filtering, text search, and sorting operations are performed in-memory rather than in the database.
 * This design choice is driven by the fact that claim values are stored in a separate table (collected_claims)
 * with a generic key-value structure, making SQL-based cross-claim filtering and sorting impractical — especially
 * across different database engines (H2 and PostgreSQL). This approach is consistent with the pattern used by
 * the other admin listings (ex. [com.sympauthy.business.manager.ClaimSearchManager]).
 *
 * This design should remain sustainable up to thousands of users, which is beyond the intended scale for SympAuthy.
 */
@Singleton
class UserSearchManager(
    @Inject private val userRepository: UserRepository,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val claimManager: ClaimManager,
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val claimValueValidator: ClaimValueValidator,
    @Inject private val userMapper: UserMapper,
    @Inject private val collectedClaimMapper: CollectedClaimMapper
) {

    /**
     * Read the page [pageParams] names of the users the criteria keep, each with their claims.
     *
     * Every criterion is optional and they compose: [status] keeps the users in one status, [query] is a
     * partial case-insensitive match across the values of every enabled claim, and [claimFilters] are
     * exact-match values keyed by claim id. [sort] and [order] are the order the page is read in, which
     * [getUserComparator] builds.
     *
     * A criterion naming something that does not exist is the caller's mistake rather than an empty result: an
     * unknown claim id or sort property each throw a recoverable business exception carrying
     * `user.search.invalid_claim` or `user.search.invalid_sort`.
     */
    suspend fun listUsers(
        status: UserStatus?,
        query: String?,
        claimFilters: Map<String, String>,
        sort: String?,
        order: SortOrder?,
        pageParams: PageParams
    ): Page<UserWithClaims> {
        // Built before the search so a sort property naming nothing is refused without reading every user.
        val comparator = getUserComparator(sort, order)
        val enabledClaims = claimManager.listEnabledClaims()

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

        val userEntities = if (status != null) {
            userRepository.findByStatusAndSessionIdIsNull(status.name).toList()
        } else {
            userRepository.findBySessionIdIsNull().toList()
        }

        if (userEntities.isEmpty()) {
            return emptyList<SearchedUser>().orderedPage(pageParams, comparator).map { toUserWithClaims(it) }
        }

        val users = userEntities.map(userMapper::toUser)
        val userIds = users.map { it.id }

        val claimEntities = collectedClaimRepository.findByUserIdInList(userIds)
        val collectedClaims = claimEntities.mapNotNull(collectedClaimMapper::toCollectedClaim)
        val claimsByUserId = collectedClaims.groupBy { it.userId }
        // Read from the rows rather than from the claims they became: a row whose claim the
        // configuration no longer declares does not map, and it still dates the user's last update.
        val latestCollectionDates = claimEntities
            .groupBy { it.userId }
            .mapValues { (_, rows) -> rows.maxOf { it.collectionDate } }

        var result = users.map { user ->
            SearchedUser(
                user = user,
                collectedClaims = claimsByUserId[user.id] ?: emptyList(),
                latestCollectionDate = latestCollectionDates[user.id]
            )
        }

        if (deserializedFilters.isNotEmpty()) {
            result = result.filter { searched ->
                deserializedFilters.all { (claimId, expectedValue) ->
                    searched.collectedClaims.any { cc ->
                        cc.claim.id == claimId && cc.value == expectedValue
                    }
                }
            }
        }

        if (!query.isNullOrBlank()) {
            val lowerQuery = query.lowercase()
            result = result.filter { searched ->
                searched.collectedClaims.any { cc ->
                    cc.claim.enabled && cc.value?.toString()?.lowercase()?.contains(lowerQuery) == true
                }
            }
        }

        return result.orderedPage(pageParams, comparator).map { toUserWithClaims(it) }
    }

    /**
     * The row [searchedUser] is published as, carrying the claim values this server generates for
     * that user.
     *
     * This is called on the users of one page rather than on everything the criteria kept: a user
     * the order left off the page is never published, so a value computed for them is read by
     * nothing.
     */
    private suspend fun toUserWithClaims(searchedUser: SearchedUser) = UserWithClaims(
        user = searchedUser.user,
        collectedClaims = searchedUser.collectedClaims,
        generatedClaimValues = generatedClaimsManager.computeValues(
            userId = searchedUser.user.id,
            latestCollectionDate = searchedUser.latestCollectionDate
        )
    )

    /**
     * Build the order a page of [listUsers] results is returned in.
     *
     * [sort] names `created_at`, `status` or a claim id, and [order] is the direction it is read in, ascending
     * where the caller named none. A [sort] naming none of those is the caller's mistake and throws a recoverable
     * business exception carrying `user.search.invalid_sort`.
     *
     * What the caller asked to sort by decides nothing between two users holding the same value, so the order
     * ends in the user's identifier and is total. That tiebreak stays ascending under `order=desc`: it is not
     * part of what the caller asked to sort by, it is there to decide what their own key leaves undecided.
     */
    internal suspend fun getUserComparator(
        sort: String?,
        order: SortOrder?
    ): Comparator<SearchedUser> {
        val enabledClaimIds = claimManager.listEnabledClaims().map { it.id }.toSet()
        if (sort != null && sort != "created_at" && sort != "status" && sort !in enabledClaimIds) {
            throw recoverableBusinessExceptionOf(
                "user.search.invalid_sort",
                "description.user.search.invalid_sort",
                "property" to sort
            )
        }

        val bySortProperty: Comparator<SearchedUser> = when (sort) {
            null, "created_at" -> compareBy { it.user.creationDate }
            "status" -> compareBy { it.user.status.name }
            // A claim holds at most one row per user, so the value this reads is the user's own and does not
            // vary between two calls.
            else -> compareBy<SearchedUser, String?>(nullsLast()) { searched ->
                searched.collectedClaims
                    .firstOrNull { it.claim.id == sort }
                    ?.value
                    ?.toString()
            }
        }

        return (if (order == SortOrder.DESC) bySortProperty.reversed() else bySortProperty)
            .thenBy { it.user.id }
    }

    /**
     * List the claims a caller asked to read the values of, or null where they asked for none.
     *
     * A null [claimIds] is every enabled claim, since a caller naming none is answered with all of
     * them, and an empty [claimIds] is a caller who asked for no claim at all. Otherwise every one
     * of [claimIds] must name an enabled claim, and one that does not throws
     * `user.search.invalid_claim`.
     */
    suspend fun listSelectedClaims(claimIds: List<String>?): List<Claim>? = when {
        claimIds == null -> claimManager.listEnabledClaims()
        claimIds.isEmpty() -> null
        else -> validateAndResolveClaimIds(claimIds)
    }

    /**
     * Validate that the given claim IDs reference valid enabled claims.
     * Returns the list of matching [Claim] objects.
     */
    internal fun validateAndResolveClaimIds(claimIds: List<String>): List<Claim> {
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

    /**
     * A user as a search reads them: what the filters, the text search and the order run over.
     *
     * It carries what was read from the tables and nothing this server computes, so that a user the
     * criteria drop, or the order leaves off the page, costs no more than the read that found them.
     *
     * [latestCollectionDate] takes no part in the search. It is the date of the most recent claim
     * collected from the user, over every row the table holds for them, read in the pass the search
     * already makes over those rows so that publishing the page does not query for it one user at a
     * time.
     */
    internal data class SearchedUser(
        val user: User,
        val collectedClaims: List<CollectedClaim>,
        val latestCollectionDate: LocalDateTime?
    )

    /**
     * A user, and every claim value a caller listing them may publish.
     *
     * The values come from two places — [collectedClaims] is what was collected from the user, and
     * [generatedClaimValues] what this server computes for them, keyed by claim identifier — and
     * they are read together so that nothing computes one claim of one row at a time.
     */
    data class UserWithClaims(
        val user: User,
        val collectedClaims: List<CollectedClaim>,
        val generatedClaimValues: Map<String, Any?>
    )
}

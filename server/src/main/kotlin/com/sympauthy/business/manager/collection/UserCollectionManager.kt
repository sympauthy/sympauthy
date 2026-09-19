package com.sympauthy.business.manager.collection

import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.UUID
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import kotlinx.coroutines.flow.toList

/**
 * Manager responsible for reading the collection of users, each with their collected claims.
 *
 * Every criterion, the free text and the order are applied in memory rather than in the database.
 * This design choice is driven by the fact that claim values are stored in a separate table (collected_claims)
 * with a generic key-value structure, making SQL-based cross-claim filtering and sorting impractical — especially
 * across different database engines (H2 and PostgreSQL). This approach is consistent with the pattern used by
 * the other admin collections (ex. [ClaimCollectionManager]).
 *
 * This design should remain sustainable up to thousands of users, which is beyond the intended scale for SympAuthy.
 */
@Singleton
class UserCollectionManager(
    @Inject private val userRepository: UserRepository,
    @Inject private val collectedClaimRepository: CollectedClaimRepository,
    @Inject private val claimManager: ClaimManager,
    @Inject private val generatedClaimsManager: GeneratedClaimsManager,
    @Inject private val userMapper: UserMapper,
    @Inject private val collectedClaimMapper: CollectedClaimMapper
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of the users [criteria] keep, each with their claims.
     */
    suspend fun listUsers(
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<UserWithClaims> {
        val userEntities = userRepository.findBySessionIdIsNull().toList()
        if (userEntities.isEmpty()) {
            return fields().page(emptyList(), criteria, pageParams).map { toUserWithClaims(it) }
        }

        val users = userEntities.map(userMapper::toUser)
        val claimEntities = collectedClaimRepository.findByUserIdInList(users.map(User::id))
        val claimsByUserId = claimEntities
            .mapNotNull(collectedClaimMapper::toCollectedClaim)
            .groupBy(CollectedClaim::userId)
        // Read from the rows rather than from the claims they became: a row whose claim the
        // configuration no longer declares does not map, and it still dates the user's last update.
        val latestCollectionDates = claimEntities
            .groupBy { it.userId }
            .mapValues { (_, rows) -> rows.maxOf { it.collectionDate } }

        val row = users.map { user ->
            UserRow(
                user = user,
                collectedClaims = claimsByUserId[user.id] ?: emptyList(),
                latestCollectionDate = latestCollectionDates[user.id]
            )
        }
        return fields().page(row, criteria, pageParams).map { toUserWithClaims(it) }
    }

    /**
     * The fields this collection offers: the account's own, then one per claim this deployment
     * collects, ending on the user identifier — which is unique by construction and is therefore not
     * one a caller may order by.
     *
     * **A claim is a field named by its own identifier, typed as the claim is typed and named as the
     * claim is named.** That is what lets a console render a filter on a claim this deployment
     * configured without having been built knowing it existed.
     *
     * **A generated claim is not one of them.** Its value is computed per account, so filtering on
     * one would mean computing it for every account rather than for the page — and the free text has
     * never matched one, since nothing is collected for it.
     *
     * **A claim named as one of the account's own fields is shadowed by it.** The capability
     * document says which the collection took, so a caller reading it is never told a field is there
     * and then answered about another one.
     */
    private suspend fun fields(): CollectionFields<UserRow> = collectionFields(
        uniqueKey = compareBy { it.user.id }
    ) {
        field("id", UUID, key = "fields.user_id") { it.user.id }
        enumeration<UserStatus>("status", key = "fields.user_status", sortable = true) { it.user.status }
        field("created_at", DATE_TIME, sortable = true) { it.user.creationDate }

        val ownFields = setOf("id", "status", "created_at")
        claimManager.listEnabledClaims()
            .filterNot(Claim::generated)
            .filterNot { it.id in ownFields }
            .forEach { claim ->
                field(
                    name = claim.id,
                    type = claim.dataType.asFieldType,
                    key = "claims.${claim.id}",
                    values = claim.allowedFieldValues,
                    nullable = true,
                    sortable = true,
                    searchable = true
                ) { row ->
                    row.collectedClaims.firstOrNull { it.claim.id == claim.id }?.value
                }
            }
        defaultSort("created_at")
    }

    /**
     * The row [row] is published as, carrying the claim values this server generates for
     * that user.
     *
     * This is called on the users of one page rather than on everything the criteria kept: a user
     * the order left off the page is never published, so a value computed for them is read by
     * nothing.
     */
    private suspend fun toUserWithClaims(row: UserRow) = UserWithClaims(
        user = row.user,
        collectedClaims = row.collectedClaims,
        generatedClaimValues = generatedClaimsManager.computeValues(
            userId = row.user.id,
            latestCollectionDate = row.latestCollectionDate
        )
    )

    /**
     * List the claims a caller asked to read the values of, or null where they asked for none.
     *
     * A null [claimIds] is every enabled claim, since a caller naming none is answered with all of
     * them, and an empty [claimIds] is a caller who asked for no claim at all. Otherwise every one
     * of [claimIds] must name an enabled claim, and one that does not throws
     * `user.claims.unknown`.
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
                "user.claims.unknown",
                "description.user.claims.unknown",
                "claim" to id
            )
        }
    }

    /**
     * A user as this collection reads them: what the criteria, the free text and the order run over.
     *
     * It carries what was read from the tables and nothing this server computes, so that a user the
     * criteria drop, or the order leaves off the page, costs no more than the read that found them.
     *
     * [latestCollectionDate] takes no part in the criteria. It is the date of the most recent claim
     * collected from the user, over every row the table holds for them, read in the pass this
     * manager already makes over those rows so that publishing the page does not query for it one
     * user at a time.
     */
    internal data class UserRow(
        val user: User,
        val collectedClaims: List<CollectedClaim>,
        val latestCollectionDate: LocalDateTime?
    )

    /**
     * A user, and every claim value a caller collection them may publish.
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

/**
 * The kind of value a claim of this type holds, which is what decides the operators a filter on it
 * admits and how a word a caller sent for it is read.
 */
private val ClaimDataType.asFieldType: CollectionFieldType
    get() = when (this) {
        ClaimDataType.BOOLEAN -> CollectionFieldType.BOOLEAN
        ClaimDataType.DATE -> CollectionFieldType.DATE
        ClaimDataType.EMAIL -> CollectionFieldType.EMAIL
        ClaimDataType.NUMBER -> CollectionFieldType.NUMBER
        ClaimDataType.PHONE_NUMBER -> CollectionFieldType.PHONE_NUMBER
        ClaimDataType.STRING -> CollectionFieldType.STRING
        ClaimDataType.TIMEZONE -> CollectionFieldType.TIMEZONE
    }

/**
 * The values a filter on this claim enumerates, which is the set this deployment restricted it to,
 * and null where it restricted it to none.
 */
private val Claim.allowedFieldValues: List<CollectionFieldValue>?
    get() = allowedValues?.map {
        val value = it.toString()
        CollectionFieldValue(value, "claims.$id.$value")
    }

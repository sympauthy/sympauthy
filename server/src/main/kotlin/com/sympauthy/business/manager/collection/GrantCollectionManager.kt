package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.collection.fieldValues
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.grant.GrantStatus
import com.sympauthy.business.model.grant.GrantWithStatus
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*
import com.sympauthy.business.model.collection.CollectionFieldType.UUID as UUID_FIELD

/**
 * Reads the grants this server still holds, for an administrator looking at what a person, a client or an
 * audience holds.
 *
 * **Every grant still stored is read, with its status**, revoked, expired and lapsed ones included: an
 * administrator who has just revoked a grant sees that it took rather than seeing it vanish. A grant the
 * cleanup job has collected is gone from here as it is from everywhere else.
 *
 * **The collection is read in memory**, as every other admin collection is — see [UserCollectionManager] for
 * the reason. The identifier claims `q` matches are read only where a `q` was sent, since nothing else reads
 * them.
 */
@Singleton
class GrantCollectionManager(
    @Inject private val grantManager: GrantManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val audienceManager: AudienceManager,
    @Inject private val scopeManager: ScopeManager
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of the grants [criteria] keep, each with where it stands now.
     */
    suspend fun listGrants(
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<GrantWithStatus> {
        val grants = grantManager.listWithStatus()
        val claimsByUserId = if (criteria.query.isNullOrBlank()) {
            emptyMap()
        } else {
            collectedClaimManager
                .listIdentifierByUserIds(grants.mapNotNull { it.grant.userId }.distinct())
                .groupBy(CollectedClaim::userId)
        }
        val rows = grants.map { GrantRow(it, it.grant.userId?.let(claimsByUserId::get).orEmpty()) }
        return fields().page(rows, criteria, pageParams).map(GrantRow::grant)
    }

    /**
     * Read the grant [id] with where it stands now, or null where this server holds none under that
     * identifier — including one the cleanup job has collected.
     */
    suspend fun findGrantOrNull(id: UUID): GrantWithStatus? = grantManager.findWithStatusByIdOrNull(id)

    /**
     * The fields this collection offers, ending on the grant identifier, which is unique by construction —
     * and which is therefore not one a caller may order by.
     *
     * **A client and an audience are offered whether the configuration still declares them or not**, since a
     * grant opened for one it no longer declares is still stored and still revocable; one no longer declared
     * is named by its identifier.
     *
     * **`scope` reads the three sets a grant holds**, granted, consented and given to the client, so it is
     * satisfied by a scope in any of them and `ne` excludes a grant holding it in any of them.
     *
     * **Each identifier claim is a field `q` matches and no criterion names**, so a person's grants are found
     * by typing their address. The grant's own fields and the grammar's parameters take precedence over a
     * claim of the same name, which is then left out rather than published as a field answering about
     * something else.
     */
    private suspend fun fields(): CollectionFields<GrantRow> = collectionFields(
        uniqueKey = compareBy { it.grant.grant.id }
    ) {
        field("id", UUID_FIELD, key = "fields.grant_id") { it.grant.grant.id }
        field("user_id", UUID_FIELD, nullable = true) { it.grant.grant.userId }
        field(
            name = "client_id",
            type = ENUM,
            values = fieldValues(
                "fields.client_id",
                (clientManager.listClients().map(Client::id) + grantManager.listClientIds()).distinct()
            ),
            searchable = true
        ) { it.grant.grant.clientId }
        field(
            name = "audience_id",
            type = ENUM,
            values = fieldValues(
                "fields.audience_id",
                (audienceManager.listAudiences().map(Audience::id) + grantManager.listAudienceIds()).distinct()
            ),
            searchable = true
        ) { it.grant.grant.audienceId }
        field(
            name = "grant_type",
            type = ENUM,
            values = GRANT_TYPES.map { CollectionFieldValue(it.wireName, "fields.grant_type.${it.wireName}") }
        ) { it.grant.grant.grantType.wireName }
        enumeration<GrantStatus>("status", key = "fields.grant_status") { it.grant.status }
        field(
            name = "scope",
            type = ENUM,
            values = fieldValues("fields.scope", scopeManager.listEnabledScopes().map(EnabledScope::scope))
        ) { it.grant.grant.allScopes }
        field("created_at", DATE_TIME, sortable = true) { it.grant.grant.creationDate }
        field("expires_at", DATE_TIME, nullable = true, sortable = true) { it.grant.grant.expirationDate }
        field("revoked_at", DATE_TIME, nullable = true, sortable = true) { it.grant.grant.revokedAt }
        enumeration<GrantRevokedBy>("revoked_by", key = "fields.grant_revoked_by", nullable = true) {
            it.grant.grant.revokedBy
        }
        field("actor_grant_id", UUID_FIELD, nullable = true) { it.grant.grant.actorGrantId }

        claimManager.listIdentifierClaims()
            .filterNot { it.id in SHADOWED_CLAIM_IDS }
            .forEach { claim ->
                field(
                    name = claim.id,
                    type = STRING,
                    key = "claims.${claim.id}",
                    searchable = true,
                    operators = emptySet()
                ) { row -> row.identifierClaims.filter { it.claim.id == claim.id }.mapNotNull(CollectedClaim::value) }
            }
        defaultSort("created_at")
    }

    private companion object {

        /**
         * The grant types a grant is opened under. A refresh issues its tokens under the grant of the refresh
         * token it was given and opens none, so no grant is ever of that type.
         */
        val GRANT_TYPES = GrantType.entries.filterNot { it == GrantType.REFRESH_TOKEN }

        /**
         * The names an identifier claim may not take a field under: the grant's own fields, and the
         * parameters [com.sympauthy.api.util.collectionCriteriaOf] reads for something other than a criterion.
         */
        val SHADOWED_CLAIM_IDS = setOf(
            "id", "user_id", "client_id", "audience_id", "grant_type", "status", "scope", "created_at",
            "expires_at", "revoked_at", "revoked_by", "actor_grant_id", "page", "size", "sort", "q"
        )
    }

    /**
     * A grant as this collection reads it: the grant with its status, and the identifier claims of the person
     * it was opened for, which only `q` reads.
     */
    internal data class GrantRow(
        val grant: GrantWithStatus,
        val identifierClaims: List<CollectedClaim>
    )
}

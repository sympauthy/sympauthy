package com.sympauthy.business.collection.manager

import com.sympauthy.business.audience.manager.AudienceManager
import com.sympauthy.business.audience.model.Audience
import com.sympauthy.business.claim.manager.ClaimManager
import com.sympauthy.business.claim.model.Claim
import com.sympauthy.business.claim.model.ClaimDataType
import com.sympauthy.business.claim.model.ClaimGroup
import com.sympauthy.business.claim.model.ClaimKind
import com.sympauthy.business.claim.model.ClaimOrigin
import com.sympauthy.business.claim.model.isEnabled
import com.sympauthy.business.collection.model.CollectionCapabilities
import com.sympauthy.business.collection.model.CollectionCriteria
import com.sympauthy.business.collection.model.CollectionFieldType.BOOLEAN
import com.sympauthy.business.collection.model.CollectionFieldType.ENUM
import com.sympauthy.business.collection.model.CollectionFieldType.STRING
import com.sympauthy.business.collection.model.CollectionFields
import com.sympauthy.business.collection.model.Page
import com.sympauthy.business.collection.model.PageParams
import com.sympauthy.business.collection.model.collectionFields
import com.sympauthy.business.collection.model.fieldValues
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager responsible for filtering the claims configured on this authorization server.
 *
 * The claims are configuration held in memory, so a criterion is a filter over the whole list rather
 * than a query, and the list is read whole before a page of it is answered.
 *
 * It filters what [ClaimManager] knows, the claims this deployment turned off included: an
 * administrator reading the configuration is who they are listed for.
 */
@Singleton
class ClaimCollectionManager(
    @Inject private val claimManager: ClaimManager,
    @Inject private val audienceManager: AudienceManager
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of every [Claim] [criteria] keep.
     */
    suspend fun listClaims(
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<Claim> = fields().page(claimManager.listAllClaims(), criteria, pageParams)

    /**
     * The fields this collection offers, ending on the claim identifier, which is unique by
     * construction — and which is therefore not one a caller may order by.
     */
    private suspend fun fields(): CollectionFields<Claim> = collectionFields(
        uniqueKey = compareBy(Claim::id)
    ) {
        val identifierClaimIds = claimManager.listIdentifierClaims().map(Claim::id).toSet()
        field("id", STRING, key = "fields.claim_id", searchable = true, read = Claim::id)
        field("enabled", BOOLEAN, sortable = true) { it.isEnabled }
        field("identifier", BOOLEAN, sortable = true) { it.id in identifierClaimIds }
        field("required", BOOLEAN, sortable = true, read = Claim::required)
        field("generated", BOOLEAN, sortable = true, read = Claim::generated)
        enumeration<ClaimOrigin>("origin", key = "fields.claim_origin", sortable = true) { it.origin }
        enumeration<ClaimKind>("kind", key = "fields.claim_kind", nullable = true, sortable = true) { it.kind }
        enumeration<ClaimDataType>("data_type", key = "fields.claim_data_type", sortable = true) { it.dataType }
        enumeration<ClaimGroup>("group", key = "fields.claim_group", nullable = true, sortable = true) { it.group }
        field(
            name = "audience_id",
            type = ENUM,
            key = "fields.audience_id",
            values = fieldValues("fields.audience_id", audienceManager.listAudiences().map(Audience::id)),
            nullable = true,
            sortable = true,
            read = Claim::audienceId
        )
    }
}

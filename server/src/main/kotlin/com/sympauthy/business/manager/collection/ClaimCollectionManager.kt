package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.BOOLEAN
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.collection.fieldValues
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ClaimGroup
import com.sympauthy.business.model.user.claim.ClaimOrigin
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
     * Read the page [pageParams] names of every [Claim] [criteria] keep, the ones this deployment
     * serves first where the caller named no order of their own.
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
        field("id", STRING, key = "fields.claim_id", searchable = true, read = Claim::id)
        field("enabled", BOOLEAN, sortable = true, read = Claim::enabled)
        field("required", BOOLEAN, sortable = true, read = Claim::required)
        field("generated", BOOLEAN, sortable = true, read = Claim::generated)
        enumeration<ClaimOrigin>("origin", key = "fields.claim_origin", sortable = true) { it.origin }
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
        defaultSort("-enabled")
    }
}

package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.BOOLEAN
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.collection.fieldValues
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.oauth2.isEnabled
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.user.claim.Claim
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager responsible for filtering the scopes this authorization server knows about, each with the
 * claims requesting it protects.
 *
 * The scopes are configuration held in memory, so a criterion is a filter over the whole list rather
 * than a query, and the list is read whole before a page of it is answered.
 *
 * Only an administrator reading the configuration has any use for it: everything that decides what a
 * token may carry lists the enabled scopes from [ScopeManager] instead.
 */
@Singleton
class ScopeCollectionManager(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val audienceManager: AudienceManager
) {

    /**
     * What this collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = fields().capabilities

    /**
     * Read the page [pageParams] names of every [Scope] [criteria] keep, each with the claims
     * requesting it protects.
     *
     * The claims are read for the scopes of one page rather than for everything the criteria kept,
     * so a scope the order left off costs nothing beyond the configuration it was read from.
     */
    suspend fun listScopes(
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<ScopeWithClaims> = fields()
        .page(scopeManager.listAllScopes(), criteria, pageParams)
        .map { ScopeWithClaims(it, scopeManager.listClaimsProtectedByScope(it)) }

    /**
     * The fields this collection offers, ending on the scope, which is unique by construction — and
     * which is therefore not one a caller may order by.
     */
    private suspend fun fields(): CollectionFields<Scope> = collectionFields(
        uniqueKey = compareBy(Scope::scope)
    ) {
        field("scope", STRING, searchable = true, read = Scope::scope)
        enumeration<ScopeType>("type", key = "fields.scope_type", sortable = true, read = Scope::type)
        field("enabled", BOOLEAN, sortable = true) { it.isEnabled }
        field(
            name = "audience_id",
            type = ENUM,
            key = "fields.audience_id",
            values = fieldValues("fields.audience_id", audienceManager.listAudiences().map(Audience::id)),
            nullable = true,
            sortable = true,
            read = Scope::audienceId
        )
    }

    /**
     * A scope, and the claims that requesting it protects.
     *
     * What a scope protects is what an administrator reading the configuration asks about it, so a
     * collection answers with both rather than leaving its caller to ask again once per scope it
     * published.
     */
    data class ScopeWithClaims(
        val scope: Scope,
        val protectedClaims: List<Claim>
    )
}

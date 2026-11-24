package com.sympauthy.business.collection.manager

import com.sympauthy.business.audience.manager.AudienceManager
import com.sympauthy.business.audience.model.Audience
import com.sympauthy.business.claim.model.Claim
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
import com.sympauthy.business.collection.model.map
import com.sympauthy.business.scope.manager.ScopeManager
import com.sympauthy.business.scope.model.Scope
import com.sympauthy.business.scope.model.ScopeType
import com.sympauthy.business.scope.model.isEnabled
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

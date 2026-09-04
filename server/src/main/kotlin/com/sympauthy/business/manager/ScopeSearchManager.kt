package com.sympauthy.business.manager

import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.oauth2.isEnabled
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.map
import com.sympauthy.business.model.page.orderedPage
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
class ScopeSearchManager(
    @Inject private val scopeManager: ScopeManager
) {

    /**
     * Read the page [pageParams] names of every [Scope] the criteria keep, ordered by scope, each
     * with the claims requesting it protects.
     *
     * [type] and [enabled] compose, and each keeps every scope where the caller named no criterion.
     * [enabled] is whether this deployment serves the scope, and a [type] naming no [ScopeType]
     * keeps nothing rather than everything.
     */
    suspend fun listScopes(
        type: ValueFilter<ScopeType>,
        enabled: Boolean?,
        pageParams: PageParams
    ): Page<ScopeWithClaims> {
        return scopeManager.listAllScopes()
            .filter { type.matches(it.type) }
            .filter { enabled == null || it.isEnabled == enabled }
            .orderedPage(pageParams, compareBy { it.scope })
            .map { ScopeWithClaims(it, scopeManager.listClaimsProtectedByScope(it)) }
    }

    /**
     * A scope, and the claims that requesting it protects.
     *
     * What a scope protects is what an administrator reading the configuration asks about it, so a
     * listing answers with both rather than leaving its caller to ask again once per scope it
     * published.
     */
    data class ScopeWithClaims(
        val scope: Scope,
        val protectedClaims: List<Claim>
    )
}

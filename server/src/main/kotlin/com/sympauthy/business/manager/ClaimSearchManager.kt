package com.sympauthy.business.manager

import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.filter.matches
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.orderedPage
import com.sympauthy.business.model.user.claim.Claim
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
class ClaimSearchManager(
    @Inject private val claimManager: ClaimManager
) {

    /**
     * Read the page [pageParams] names of every [Claim] the criteria keep, the ones this deployment
     * serves first and each group ordered by identifier.
     *
     * [enabled], [required] and [origin] compose, and each keeps every claim where the caller named
     * no criterion. An [origin] naming no [ClaimOrigin] keeps nothing rather than everything.
     */
    suspend fun listClaims(
        enabled: Boolean?,
        required: Boolean?,
        origin: ValueFilter<ClaimOrigin>,
        pageParams: PageParams
    ): Page<Claim> {
        return claimManager.listAllClaims()
            .filter { enabled == null || it.enabled == enabled }
            .filter { required == null || it.required == required }
            .filter { origin.matches(it.origin) }
            .orderedPage(pageParams, compareByDescending<Claim> { it.enabled }.thenBy { it.id })
    }
}

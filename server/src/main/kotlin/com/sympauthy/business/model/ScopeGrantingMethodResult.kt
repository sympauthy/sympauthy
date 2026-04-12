package com.sympauthy.business.model

import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.oauth2.Scope

/**
 * Result containing all granted/declined scopes be the application of a scope granting method.
 *
 * @see [com.sympauthy.business.manager.rule.ScopeGrantingRuleManager.applyScopeGrantingRules]
 */
data class ScopeGrantingMethodResult(
    /**
     * Identifies which granting method produced this result.
     * null for the auto-granted partition which is not a granting method.
     */
    val source: GrantedBy? = null,
    val grantedScopes: List<Scope> = emptyList(),
    val declinedScopes: List<Scope> = emptyList()
)

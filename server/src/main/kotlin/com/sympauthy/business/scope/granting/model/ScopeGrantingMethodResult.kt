package com.sympauthy.business.scope.granting.model

import com.sympauthy.business.scope.model.EnabledScope

/**
 * Result containing all granted/declined scopes be the application of a scope granting method.
 *
 * @see [com.sympauthy.business.scope.granting.manager.ScopeGrantingRuleManager.applyScopeGrantingRules]
 */
data class ScopeGrantingMethodResult(
    /**
     * Identifies which granting method produced this result.
     * null for the auto-granted partition which is not a granting method.
     */
    val source: GrantedBy? = null,
    val grantedScopes: List<EnabledScope> = emptyList(),
    val declinedScopes: List<EnabledScope> = emptyList()
)

package com.sympauthy.business.model

import com.sympauthy.business.model.oauth2.Scope

/**
 * Result containing all granted/declined scopes be the application of a scope granting method.
 *
 * @see [com.sympauthy.business.manager.rule.ScopeGrantingRuleManager.applyScopeGrantingRules]
 */
data class ScopeGrantingMethodResult(
    val grantedScopes: List<Scope>,
    val declinedScopes: List<Scope>
)
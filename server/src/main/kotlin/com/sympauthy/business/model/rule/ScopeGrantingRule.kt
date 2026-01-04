package com.sympauthy.business.model.rule

import com.sympauthy.business.model.oauth2.Scope

/**
 * A rule granting/declining a scope requested by a user authenticating through a flow.
 */
class ScopeGrantingRule(
    /**
     * Name defined by the user for this rule.
     *
     * @see name
     */
    val userDefinedName: String?,
    /**
     * Behavior of this rule when all expressions are matched.
     */
    val behavior: ScopeGrantingRuleBehavior,
    /**
     * A number defining an order of priority when multiple rules are matched by an authorization attempt.
     * **Higher** have priority over **lower**.
     */
    val order: Int,
    /**
     * List of [Scope] that will be granted/declined by this rule.
     */
    val scopes: List<Scope>,
    /**
     * List of user-defined expressions that will be evaluated to check if an authorization attempt matches this rule.
     */
    val expressions: List<String>
) {

    /**
     * Display name for this rule. It will be used in all logs (including access) to refer to this rule.
     * If the user does not provide a [userDefinedName] in the configuration, the [generatedName] will be used instead.
     */
    val name: String
        get() = userDefinedName ?: generatedName

    /**
     * Generated name for this rule.
     *
     * @see name
     */
    val generatedName: String
        get() = "$order - ${behavior.name} ${scopes.joinToString(separator = ", ", transform = Scope::scope)}"

    /**
     * Return the intersection of scopes that are both in the [requestedScopes] list and in the list of [scopes]
     * granted/declined by this rule.
     */
    fun getApplicableScopes(requestedScopes: List<Scope>): List<Scope> {
        return requestedScopes.filter { scopes.contains(it) }
    }
}

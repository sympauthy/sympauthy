package com.sympauthy.business.model.rule

/**
 * A rule authorizing (or forbidding) an acting client to obtain an access token that acts on behalf of a target user
 * via OAuth 2.0 Token Exchange (RFC 8693).
 *
 * Unlike [ScopeGrantingRule], an act-as rule is permission-only: it does not grant any scope. It only decides whether
 * the delegation is permitted for the (acting client, target user) pair being evaluated.
 */
class ActAsRule(
    /**
     * Name defined by the user for this rule.
     *
     * @see name
     */
    val userDefinedName: String?,
    /**
     * Behavior of this rule when all expressions are matched.
     */
    val behavior: ActAsRuleBehavior,
    /**
     * A number defining an order of priority when multiple rules are matched.
     * **Higher** have priority over **lower**.
     */
    val order: Int,
    /**
     * List of user-defined expressions that will be evaluated to check if a delegation request matches this rule.
     * All expressions must evaluate to true for the rule to match.
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
        get() = "$order - ${behavior.name}"
}

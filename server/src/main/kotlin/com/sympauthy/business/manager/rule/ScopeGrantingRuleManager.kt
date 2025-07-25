package com.sympauthy.business.manager.rule

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.rule.ScopeGrantingRule
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.DECLINE
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.GRANT
import com.sympauthy.config.model.ScopeGrantingRulesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject

class ScopeGrantingRuleManager(
    @Inject private val uncheckedScopeGrantingRulesConfig: ScopeGrantingRulesConfig,
) {

    /**
     * Return all [ScopeGrantingRule] enabled on this authorization server.
     */
    fun listScopeGrantingRules(): List<ScopeGrantingRule> {
        return uncheckedScopeGrantingRulesConfig.orThrow().scopeGrantingRules
    }

    /**
     * Apply the configured [ScopeGrantingRule] to the [requestedScopes] to
     * determine which of the [requestedScopes] are granted.
     */
    suspend fun applyScopeGrantingRules(
        authorizeAttempt: AuthorizeAttempt,
        requestedScopes: List<Scope>,
    ): ScopeGrantingMethodResult {
        val results = findApplicableScopeGrantingRules(requestedScopes).map {
            applyRule(
                authorizeAttempt = authorizeAttempt,
                requestedScopes = requestedScopes,
                rule = it
            )
        }
        return mergeResult(
            requestedScopes = requestedScopes,
            results = results,
        )
    }

    suspend fun applyRule(
        authorizeAttempt: AuthorizeAttempt,
        requestedScopes: List<Scope>,
        rule: ScopeGrantingRule,
    ): ScopeGrantingRuleResult {
        return TODO()
    }

    /**
     * Merge the [results] of all applicable [ScopeGrantingRule] according to the
     * [ScopeGrantingRule.order].
     *
     * Following rules are applied when they have conflicting scopes:
     * - A [ScopeGrantingRule] rule with greater [ScopeGrantingRule.order]
     *   will override any [ScopeGrantingRule] of lower [ScopeGrantingRule.order].
     * - A [ScopeGrantingRule] with [ScopeGrantingRule.behavior]
     *   set to [DECLINE] will always win over
     *   any number of [ScopeGrantingRule] of same or lower [ScopeGrantingRule.order].
     */
    fun mergeResult(
        requestedScopes: List<Scope>,
        results: List<ScopeGrantingRuleResult>
    ): ScopeGrantingMethodResult {
        val sortedResults = results.sortedWith(
            compareByDescending<ScopeGrantingRuleResult> { it.rule.order }
                .thenByDescending {
                    when (it.rule.behavior) {
                        DECLINE -> 1
                        GRANT -> 0
                    }
                }
        )

        val grantedScopes = mutableSetOf<Scope>()
        val declinedScopes = mutableSetOf<Scope>()

        requestedScopes.forEach { scope ->
            val result = sortedResults.firstOrNull { it.applicableRequestedScopes.contains(scope) }
            when (result?.rule?.behavior) {
                GRANT -> grantedScopes.add(scope)
                DECLINE -> declinedScopes.add(scope)
                null -> {} // Do nothing, nor granted, nor denied.
            }
        }

        return ScopeGrantingMethodResult(
            grantedScopes = grantedScopes.toList(),
            declinedScopes = declinedScopes.toList(),
        )
    }

    /**
     * Return the list of [ScopeGrantingRule] that are applicable according to the
     * [requestedScopes].
     *
     * @see [ScopeGrantingRule.isApplicable]
     */
    suspend fun findApplicableScopeGrantingRules(
        requestedScopes: List<Scope>,
    ): List<ScopeGrantingRule> {
        return listScopeGrantingRules().filter { it.isApplicable(requestedScopes) }
    }
}

/**
 * Result of the application of a [ScopeGrantingRule] to an [AuthorizeAttempt].
 *
 */
data class ScopeGrantingRuleResult(
    /**
     * The rule that have been applied.
     */
    val rule: ScopeGrantingRule,
    /**
     * List of [Scope] that where requested in the [AuthorizeAttempt]
     * and applicable to the [rule].
     */
    val applicableRequestedScopes: List<Scope>
)

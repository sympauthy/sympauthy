package com.sympauthy.business.manager.rule

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.rule.ScopeGrantingRule
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.DECLINE
import com.sympauthy.business.model.rule.ScopeGrantingRuleBehavior.GRANT
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.ScopeGrantingRulesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class ScopeGrantingRuleManager(
    @Inject private val scopeGrantingRuleExpressionExecutor: ScopeGrantingRuleExpressionExecutor,
    @Inject private val uncheckedScopeGrantingRulesConfigFlow: Flow<ScopeGrantingRulesConfig>,
) {
    /**
     * Return all [ScopeGrantingRule] enabled on this authorization server.
     */
    suspend fun listScopeGrantingRules(): List<ScopeGrantingRule> {
        return uncheckedScopeGrantingRulesConfigFlow.firstOrNull()?.orThrow()?.scopeGrantingRules ?: emptyList()
    }

    /**
     * Apply the configured [ScopeGrantingRule] to the [requestedScopes] to
     * determine which of the [requestedScopes] are granted.
     */
    suspend fun applyScopeGrantingRules(
        authorizeAttempt: AuthorizeAttempt,
        requestedScopes: List<Scope>,
        collectedClaims: List<CollectedClaim>,
    ): ScopeGrantingMethodResult {
        val results = findApplicableScopeGrantingRules(requestedScopes).map {
            applyRule(
                rule = it.rule,
                applicableRequestedScopes = it.applicableRequestedScopes,
                authorizeAttempt = authorizeAttempt,
                collectedClaims = collectedClaims,
            )
        }
        return mergeResult(
            requestedScopes = requestedScopes,
            results = results,
        )
    }

    /**
     * Return the list of [ScopeGrantingRule] that are applicable according to the
     * [requestedScopes].
     *
     * @see [ScopeGrantingRule.getApplicableScopes]
     */
    internal suspend fun findApplicableScopeGrantingRules(
        requestedScopes: List<Scope>,
    ): List<ApplicableScopeGrantingRule> {
        return listScopeGrantingRules().mapNotNull { rule ->
            val applicableRequestedScopes = rule.getApplicableScopes(requestedScopes)
            if (applicableRequestedScopes.isNotEmpty()) {
                ApplicableScopeGrantingRule(
                    rule = rule,
                    applicableRequestedScopes = applicableRequestedScopes
                )
            } else null
        }
    }

    /**
     * Applies a specific [ScopeGrantingRule] to determine if the [applicableRequestedScopes] are granted based on the
     * provided [authorizeAttempt] and [collectedClaims].
     *
     * The [applicableRequestedScopes] are granted if all the [ScopeGrantingRule.expressions] evaluate to true.
     */
    suspend fun applyRule(
        rule: ScopeGrantingRule,
        applicableRequestedScopes: List<Scope>,
        authorizeAttempt: AuthorizeAttempt,
        collectedClaims: List<CollectedClaim>,
    ): ScopeGrantingRuleResult {
        val configuration = scopeGrantingRuleExpressionExecutor.getConfiguration(authorizeAttempt, collectedClaims)
        val isGranted = rule.expressions.all { expression ->
            try {
                scopeGrantingRuleExpressionExecutor.evaluateExpressionOrThrow(expression, configuration)
            } catch (e: InvalidScopeGrantingRuleException) {
                throw internalBusinessExceptionOf(
                    detailsId = e.businessErrorDetailsId,
                    values = arrayOf("message" to (e.message ?: ""))
                )
            }
        }
        return ScopeGrantingRuleResult(
            rule = rule,
            applicableRequestedScopes = applicableRequestedScopes,
            granted = isGranted
        )
    }

    /**
     * Merge the [results] of all applicable [ScopeGrantingRule] according to the
     * [ScopeGrantingRule.order].
     *
     * The following rules are applied when they have conflicting scopes:
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
}

data class ApplicableScopeGrantingRule(
    /**
     * The rule to apply.
     */
    val rule: ScopeGrantingRule,
    /**
     * List of [Scope] that where requested in the [AuthorizeAttempt] and applicable to the [rule].
     */
    val applicableRequestedScopes: List<Scope>
)

/**
 * Result of the application of a [ScopeGrantingRule] to an [AuthorizeAttempt].
 */
data class ScopeGrantingRuleResult(
    /**
     * The rule that has been applied.
     */
    val rule: ScopeGrantingRule,
    /**
     * List of [Scope] that where requested in the [AuthorizeAttempt]
     * and applicable to the [rule].
     */
    val applicableRequestedScopes: List<Scope>,
    /**
     * True if the execution of rule expression has granted the [applicableRequestedScopes].
     */
    val granted: Boolean = false
)

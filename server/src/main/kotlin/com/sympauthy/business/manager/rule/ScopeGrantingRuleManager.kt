package com.sympauthy.business.manager.rule

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.GrantedBy
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
     * Return all user [ScopeGrantingRule] enabled on this authorization server.
     */
    suspend fun listUserScopeGrantingRules(): List<ScopeGrantingRule> {
        return uncheckedScopeGrantingRulesConfigFlow.firstOrNull()?.orThrow()?.userScopeGrantingRules ?: emptyList()
    }

    /**
     * Return all client [ScopeGrantingRule] enabled on this authorization server.
     */
    suspend fun listClientScopeGrantingRules(): List<ScopeGrantingRule> {
        return uncheckedScopeGrantingRulesConfigFlow.firstOrNull()?.orThrow()?.clientScopeGrantingRules ?: emptyList()
    }

    /**
     * Apply the configured user [ScopeGrantingRule] to the [requestedScopes] to
     * determine which of the [requestedScopes] are granted.
     */
    suspend fun applyUserScopeGrantingRules(
        authorizeAttempt: AuthorizeAttempt,
        requestedScopes: List<Scope>,
        collectedClaims: List<CollectedClaim>,
    ): ScopeGrantingMethodResult {
        val configuration = scopeGrantingRuleExpressionExecutor.getConfiguration(authorizeAttempt, collectedClaims)
        val results = findApplicableScopeGrantingRulesAccordingToScopes(
            rules = listUserScopeGrantingRules(),
            requestedScopes = requestedScopes
        ).map {
            isRuleApplicableAccordingToExpressions(
                rule = it.rule,
                applicableRequestedScopes = it.applicableRequestedScopes,
                configuration = configuration,
            )
        }
        return mergeResult(
            requestedScopes = requestedScopes,
            results = results,
        )
    }

    /**
     * Apply the configured client [ScopeGrantingRule] to the [requestedScopes] to
     * determine which of the [requestedScopes] are granted for the [client].
     */
    suspend fun applyClientScopeGrantingRules(
        client: Client,
        requestedScopes: List<Scope>,
    ): ScopeGrantingMethodResult {
        val configuration = scopeGrantingRuleExpressionExecutor.getClientConfiguration(client)
        val results = findApplicableScopeGrantingRulesAccordingToScopes(
            rules = listClientScopeGrantingRules(),
            requestedScopes = requestedScopes
        ).map {
            isRuleApplicableAccordingToExpressions(
                rule = it.rule,
                applicableRequestedScopes = it.applicableRequestedScopes,
                configuration = configuration,
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
    internal fun findApplicableScopeGrantingRulesAccordingToScopes(
        rules: List<ScopeGrantingRule>,
        requestedScopes: List<Scope>,
    ): List<ApplicableScopeGrantingRule> {
        return rules.mapNotNull { rule ->
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
     * Applies a specific [ScopeGrantingRule] to determine if the [rule] should be applied when determining the scopes
     * that should be granted.
     *
     * The [rule] applies if all the [ScopeGrantingRule.expressions] evaluate to true.
     */
    suspend fun isRuleApplicableAccordingToExpressions(
        rule: ScopeGrantingRule,
        applicableRequestedScopes: List<Scope>,
        configuration: ExpressionConfiguration,
    ): ScopeGrantingRuleIsApplicableResult {
        val applicable = rule.expressions.all { expression ->
            try {
                scopeGrantingRuleExpressionExecutor.evaluateExpressionOrThrow(expression, configuration)
            } catch (e: InvalidScopeGrantingRuleException) {
                throw internalBusinessExceptionOf(
                    detailsId = e.businessErrorDetailsId,
                    values = arrayOf("message" to (e.message ?: ""))
                )
            }
        }
        return ScopeGrantingRuleIsApplicableResult(
            rule = rule,
            applicableRequestedScopes = applicableRequestedScopes,
            applicable = applicable
        )
    }

    /**
     * Merge the [results] of all applicable [ScopeGrantingRule] according to the [ScopeGrantingRule.order].
     *
     * The following rules are applied when they have conflicting scopes:
     * - A [ScopeGrantingRule] rule with greater [ScopeGrantingRule.order] will override any [ScopeGrantingRule]
     *   of lower [ScopeGrantingRule.order].
     * - A [ScopeGrantingRule] with [ScopeGrantingRule.behavior] set to [DECLINE] will always win over
     *   any number of [ScopeGrantingRule] of same or lower [ScopeGrantingRule.order].
     */
    fun mergeResult(
        requestedScopes: List<Scope>,
        results: List<ScopeGrantingRuleIsApplicableResult>
    ): ScopeGrantingMethodResult {
        val sortedApplicableResults = results
            .filter(ScopeGrantingRuleIsApplicableResult::applicable)
            .sortedWith(
                compareByDescending<ScopeGrantingRuleIsApplicableResult> { it.rule.order }
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
            val result = sortedApplicableResults.firstOrNull { it.applicableRequestedScopes.contains(scope) }
            when (result?.rule?.behavior) {
                GRANT -> grantedScopes.add(scope)
                DECLINE -> declinedScopes.add(scope)
                null -> {} // Do nothing, nor granted, nor denied.
            }
        }

        return ScopeGrantingMethodResult(
            source = GrantedBy.RULE,
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
     * List of [Scope] that where requested and applicable to the [rule].
     */
    val applicableRequestedScopes: List<Scope>
)

/**
 * Result of the evaluation of a [ScopeGrantingRule.expressions].
 */
data class ScopeGrantingRuleIsApplicableResult(
    /**
     * The rule that has been applied.
     */
    val rule: ScopeGrantingRule,
    /**
     * List of [Scope] that where requested and that the [rule] can grant or decline.
     */
    val applicableRequestedScopes: List<Scope>,
    /**
     * True if the [rule] applies according to the result of the evaluation of the
     * [ScopeGrantingRule.expressions].
     */
    val applicable: Boolean = false
)

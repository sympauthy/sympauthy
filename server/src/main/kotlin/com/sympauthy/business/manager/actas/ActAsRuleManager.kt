package com.sympauthy.business.manager.actas

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.rule.ActAsRule
import com.sympauthy.business.model.rule.ActAsRuleBehavior.ALLOW
import com.sympauthy.business.model.rule.ActAsRuleBehavior.DENY
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.ActAsRulesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * Evaluates the configured `rules.act_as` rules to decide whether an acting client may obtain an access token that
 * acts on behalf of a target user via OAuth 2.0 Token Exchange (RFC 8693).
 */
@Singleton
class ActAsRuleManager(
    @Inject private val actAsRuleExpressionExecutor: ActAsRuleExpressionExecutor,
    @Inject private val uncheckedActAsRulesConfigFlow: Flow<ActAsRulesConfig>,
) {

    /**
     * Return all [ActAsRule] enabled on this authorization server.
     */
    suspend fun listActAsRules(): List<ActAsRule> {
        return uncheckedActAsRulesConfigFlow.firstOrNull()?.orThrow()?.actAsRules ?: emptyList()
    }

    /**
     * Return true if the [client] is authorized to act on behalf of the target user described by [targetUserClaims]
     * according to the configured act-as rules.
     *
     * Rules are evaluated with the following precedence, mirroring the scope granting rules:
     * - a rule with a greater [ActAsRule.order] wins over any rule of lower order;
     * - at equal order, a [DENY] rule wins over an [ALLOW] rule.
     *
     * **Fail closed:** if no rule matches, the delegation is denied.
     */
    suspend fun isActAsAllowed(
        client: Client,
        targetUserClaims: List<CollectedClaim>
    ): Boolean {
        val configuration = actAsRuleExpressionExecutor.getConfiguration(client, targetUserClaims)
        val winningRule = listActAsRules()
            .filter { rule -> isRuleMatched(rule, configuration) }
            .sortedWith(
                compareByDescending<ActAsRule> { it.order }
                    .thenByDescending {
                        when (it.behavior) {
                            DENY -> 1
                            ALLOW -> 0
                        }
                    }
            )
            .firstOrNull()
        return winningRule?.behavior == ALLOW
    }

    /**
     * A rule matches when all its [ActAsRule.expressions] evaluate to true.
     */
    private suspend fun isRuleMatched(
        rule: ActAsRule,
        configuration: ExpressionConfiguration
    ): Boolean {
        return rule.expressions.all { expression ->
            try {
                actAsRuleExpressionExecutor.evaluateExpressionOrThrow(expression, configuration)
            } catch (e: InvalidActAsRuleException) {
                throw internalBusinessExceptionOf(
                    detailsId = e.businessErrorDetailsId,
                    values = arrayOf("message" to (e.message ?: ""))
                )
            }
        }
    }
}

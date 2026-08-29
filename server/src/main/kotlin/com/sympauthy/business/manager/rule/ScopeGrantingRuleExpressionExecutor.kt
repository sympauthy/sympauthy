package com.sympauthy.business.manager.rule

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.expression.ScopeGrantingRuleExpressions
import com.sympauthy.expression.evaluateRuleExpression
import jakarta.inject.Singleton

/**
 * Evaluates scope granting rule expressions against what a request actually carries.
 *
 * Whether an expression is usable at all is settled once, when the configuration is read, by
 * [com.sympauthy.expression.ScopeGrantingRuleExpressions]. What is left here is the half that needs a
 * request behind it: binding the functions to this end-user's claims or this client, and running them.
 */
@Singleton
class ScopeGrantingRuleExpressionExecutor {

    /**
     * Bind the expression functions to the [collectedClaims] of the end-user being authorized.
     */
    suspend fun getConfiguration(
        collectedClaims: List<CollectedClaim>
    ): ExpressionConfiguration = ScopeGrantingRuleExpressions.userConfiguration(collectedClaims)

    /**
     * Bind the expression functions to the [client] requesting the scopes.
     */
    fun getClientConfiguration(
        client: Client
    ): ExpressionConfiguration = ScopeGrantingRuleExpressions.clientConfiguration(client)

    /**
     * @throws com.sympauthy.expression.InvalidRuleExpressionException when [expressionString] does not
     * evaluate to a boolean, which at this point means an expression the configuration accepted has
     * failed against real values.
     */
    internal suspend fun evaluateExpressionOrThrow(
        expressionString: String,
        configuration: ExpressionConfiguration
    ): Boolean = evaluateRuleExpression(expressionString, configuration)
}

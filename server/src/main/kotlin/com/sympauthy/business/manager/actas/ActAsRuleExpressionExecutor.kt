package com.sympauthy.business.manager.actas

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.expression.ActAsRuleExpressions
import com.sympauthy.expression.evaluateRuleExpression
import jakarta.inject.Singleton

/**
 * Evaluates act-as rule expressions against what a token exchange request actually carries.
 *
 * Whether an expression is usable at all is settled once, when the configuration is read, by
 * [com.sympauthy.expression.ActAsRuleExpressions]. What is left here is the half that needs a request
 * behind it: binding the functions to the acting client and the target user, and running them.
 */
@Singleton
class ActAsRuleExpressionExecutor {

    /**
     * Bind the expression functions to the acting [client] and the [targetUserClaims] of the user
     * being acted for.
     */
    fun getConfiguration(
        client: Client,
        targetUserClaims: List<CollectedClaim>
    ): ExpressionConfiguration = ActAsRuleExpressions.configuration(client, targetUserClaims)

    /**
     * Evaluate [expressionString] against [configuration] and return what it answers.
     *
     * One that does not answer a boolean throws an
     * [com.sympauthy.expression.InvalidRuleExpressionException], which here means an expression the
     * configuration already accepted has failed against real values rather than dummy ones.
     */
    internal suspend fun evaluateExpressionOrThrow(
        expressionString: String,
        configuration: ExpressionConfiguration
    ): Boolean = evaluateRuleExpression(expressionString, configuration)
}

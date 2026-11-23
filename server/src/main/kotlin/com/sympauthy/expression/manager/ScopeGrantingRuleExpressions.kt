package com.sympauthy.expression.manager

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.model.claim.CollectedClaim
import com.sympauthy.business.model.client.Client
import com.sympauthy.expression.exception.InvalidRuleExpressionException
import java.util.Map.entry

/**
 * The functions a scope granting rule expression may call, and the two things done with them:
 * refusing an expression when the configuration is read, and evaluating one while a request is served.
 */
object ScopeGrantingRuleExpressions {

    /**
     * Configuration for a rule granting scopes to an end-user, whose [collectedClaims] the expression
     * may read.
     */
    fun userConfiguration(collectedClaims: List<CollectedClaim>): ExpressionConfiguration =
        userFunctions(collectedClaims)

    /**
     * Configuration for a rule granting scopes to a [client].
     */
    fun clientConfiguration(client: Client): ExpressionConfiguration =
        clientFunctions(client)

    /**
     * Refuse [expressionString] unless it is usable as a rule about an end-user, by evaluating it
     * against functions that exist and answer nothing. One that is not throws an
     * [InvalidRuleExpressionException] naming which of the two ways it failed.
     */
    fun validateUserExpression(expressionString: String) {
        evaluateRuleExpression(expressionString, userFunctions(collectedClaims = emptyList()))
    }

    /**
     * Refuse [expressionString] unless it is usable as a rule about a client, the same way and with
     * the same failures as a rule about an end-user.
     */
    fun validateClientExpression(expressionString: String) {
        evaluateRuleExpression(expressionString, clientFunctions(client = null))
    }

    /**
     * The function set of a rule about an end-user, bound to the [collectedClaims] of one, or to nothing
     * where there are none.
     */
    private fun userFunctions(collectedClaims: List<CollectedClaim>): ExpressionConfiguration =
        defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(collectedClaims)),
            entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(collectedClaims))
        )

    /**
     * The function set of a rule about a client, bound to the [client], or to nothing where it is null.
     */
    private fun clientFunctions(client: Client?): ExpressionConfiguration =
        defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(client))
        )
}

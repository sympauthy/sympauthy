package com.sympauthy.expression

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.CollectedClaim
import java.util.Map.entry

/**
 * The functions a scope granting rule expression may call, and the two things done with them:
 * refusing an expression when the configuration is read, and evaluating one while a request is served.
 *
 * The function set is declared here once and used for both. Declaring it twice is how a deployment
 * ends up with an expression that was accepted at startup and calls a function nothing provides.
 *
 * The two uses have separate entry points rather than one taking nullable values, because a
 * configuration built with nothing bound answers every call with null: usable for deciding whether an
 * expression is well formed, and silently wrong for deciding what a request may have.
 */
object ScopeGrantingRuleExpressions {

    /**
     * Configuration for a rule granting scopes to an end-user, whose [collectedClaims] the expression
     * may read.
     */
    fun userConfiguration(collectedClaims: List<CollectedClaim>): ExpressionConfiguration {
        return defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(collectedClaims)),
            entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(collectedClaims))
        )
    }

    /**
     * Configuration for a rule granting scopes to a [client].
     */
    fun clientConfiguration(client: Client): ExpressionConfiguration {
        return defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(client))
        )
    }

    /**
     * Refuse [expressionString] unless it is usable as a rule about an end-user, by evaluating it
     * against functions that exist and answer nothing. One that is not throws an
     * [InvalidRuleExpressionException] naming which of the two ways it failed.
     */
    fun validateUserExpression(expressionString: String) {
        val configuration = defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClaimFunction.FUNCTION_NAME, ClaimFunction()),
            entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction())
        )
        evaluateRuleExpression(expressionString, configuration)
    }

    /**
     * Refuse [expressionString] unless it is usable as a rule about a client, the same way and with
     * the same failures as a rule about an end-user.
     */
    fun validateClientExpression(expressionString: String) {
        val configuration = defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction())
        )
        evaluateRuleExpression(expressionString, configuration)
    }
}

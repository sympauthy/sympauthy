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
 */
object ScopeGrantingRuleExpressions {

    /**
     * Configuration for a rule granting scopes to an end-user, whose [collectedClaims] the expression
     * may read. Passing none yields the configuration used to refuse an expression rather than run it:
     * the functions exist, so the expression parses, and they answer nothing.
     */
    fun userConfiguration(collectedClaims: List<CollectedClaim> = emptyList()): ExpressionConfiguration {
        return defaultExpressionConfiguration.withAdditionalFunctions(
            entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(collectedClaims)),
            entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(collectedClaims))
        )
    }

    /**
     * Configuration for a rule granting scopes to a [client]. Passing none yields the configuration
     * used to refuse an expression rather than run it.
     */
    fun clientConfiguration(client: Client? = null): ExpressionConfiguration {
        return defaultExpressionConfiguration.withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(client))
        )
    }

    /**
     * @throws InvalidRuleExpressionException when [expressionString] is not a usable rule for an end-user.
     */
    fun validateUserExpression(expressionString: String) {
        evaluateRuleExpression(expressionString, userConfiguration())
    }

    /**
     * @throws InvalidRuleExpressionException when [expressionString] is not a usable rule for a client.
     */
    fun validateClientExpression(expressionString: String) {
        evaluateRuleExpression(expressionString, clientConfiguration())
    }
}

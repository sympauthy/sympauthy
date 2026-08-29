package com.sympauthy.expression

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.CollectedClaim
import java.util.Map.entry

/**
 * The functions an act-as rule expression may call, and the two things done with them: refusing an
 * expression when the configuration is read, and evaluating one while a token exchange is served.
 *
 * An act-as expression may speak about both the acting client and the user being acted for, which is
 * why it gets both function families where a scope granting rule gets one.
 */
object ActAsRuleExpressions {

    /**
     * Configuration for a rule letting [client] act for the user whose [targetUserClaims] the
     * expression may read. Passing neither yields the configuration used to refuse an expression
     * rather than run it.
     */
    fun configuration(
        client: Client? = null,
        targetUserClaims: List<CollectedClaim> = emptyList()
    ): ExpressionConfiguration {
        return defaultExpressionConfiguration.withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(client)),
            entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(targetUserClaims)),
            entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(targetUserClaims))
        )
    }

    /**
     * @throws InvalidRuleExpressionException when [expressionString] is not a usable act-as rule.
     */
    fun validateExpression(expressionString: String) {
        evaluateRuleExpression(expressionString, configuration())
    }
}

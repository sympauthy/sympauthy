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
 *
 * The two uses have separate entry points rather than one taking nullable values, for
 * [the reason given beside the scope granting rules][ScopeGrantingRuleExpressions].
 */
object ActAsRuleExpressions {

    /**
     * Configuration for a rule letting [client] act for the user whose [targetUserClaims] the
     * expression may read.
     */
    fun configuration(
        client: Client,
        targetUserClaims: List<CollectedClaim>
    ): ExpressionConfiguration {
        return defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(client)),
            entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(targetUserClaims)),
            entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(targetUserClaims))
        )
    }

    /**
     * Refuse [expressionString] unless it is usable as an act-as rule, by evaluating it against
     * functions that exist and answer nothing. One that is not throws an
     * [InvalidRuleExpressionException] naming which of the two ways it failed.
     */
    fun validateExpression(expressionString: String) {
        val configuration = defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction()),
            entry(ClaimFunction.FUNCTION_NAME, ClaimFunction()),
            entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction())
        )
        evaluateRuleExpression(expressionString, configuration)
    }
}

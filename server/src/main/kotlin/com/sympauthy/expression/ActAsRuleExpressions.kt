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
     * expression may read.
     */
    fun configuration(
        client: Client,
        targetUserClaims: List<CollectedClaim>
    ): ExpressionConfiguration = functions(client, targetUserClaims)

    /**
     * Refuse [expressionString] unless it is usable as an act-as rule, by evaluating it against
     * functions that exist and answer nothing. One that is not throws an
     * [InvalidRuleExpressionException] naming which of the two ways it failed.
     */
    fun validateExpression(expressionString: String) {
        evaluateRuleExpression(expressionString, functions(client = null, targetUserClaims = emptyList()))
    }

    /**
     * The act-as function set, bound to the acting [client] and the [targetUserClaims] of the user it acts
     * for, or to nothing where the client is null.
     */
    private fun functions(client: Client?, targetUserClaims: List<CollectedClaim>): ExpressionConfiguration =
        defaultExpressionConfiguration().withAdditionalFunctions(
            entry(ClientFunction.FUNCTION_NAME, ClientFunction(client)),
            entry(ClaimFunction.FUNCTION_NAME, ClaimFunction(targetUserClaims)),
            entry(ClaimIsVerifiedFunction.FUNCTION_NAME, ClaimIsVerifiedFunction(targetUserClaims))
        )
}

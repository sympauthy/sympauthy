package com.sympauthy.business.manager.rule.function

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import com.sympauthy.business.model.user.CollectedClaim

/**
 * Custom function checking if the claim is verified for the authenticating end-user.
 * Return true or false.
 */
@FunctionParameter(name = "claim")
class ClaimIsVerifiedFunction(
    /**
     * List of claims collected for the end-user.
     */
    val collectedClaims: List<CollectedClaim> = emptyList()
) : AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val claim = parameterValues[0].stringValue
        return EvaluationValue.booleanValue(false)
    }

    companion object {
        const val FUNCTION_NAME = "CLAIM_IS_VERIFIED"
    }
}
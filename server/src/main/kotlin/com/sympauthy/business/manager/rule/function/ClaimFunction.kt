package com.sympauthy.business.manager.rule.function

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import com.sympauthy.business.model.user.CollectedClaim

/**
 * Custom function returning the value of the claim for the authenticating end-user.
 */
@FunctionParameter(name = "claim")
class ClaimFunction(
    private val collectedClaims: List<CollectedClaim> = emptyList()
) : AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        return TODO("FIXME")
    }

    companion object {
        const val FUNCTION_NAME = "CLAIM"
    }
}
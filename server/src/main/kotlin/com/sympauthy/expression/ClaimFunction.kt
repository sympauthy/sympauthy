package com.sympauthy.expression

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.claim.ClaimDataType

/**
 * Custom function returning the value of the claim as a string for the authenticating end-user.
 *
 * A [ClaimDataType.JSON] claim answers null, as one holding no value does: the text of a document is not
 * a value an expression could mean to compare, and the string a map renders into is not even JSON.
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
        if (parameterValues.isEmpty()) {
            return EvaluationValue.NULL_VALUE
        }
        val claim = parameterValues[0].stringValue
        val collectedClaim = collectedClaims.find { it.claim.id == claim } ?: return EvaluationValue.NULL_VALUE
        if (collectedClaim.claim.dataType == ClaimDataType.JSON) return EvaluationValue.NULL_VALUE
        val value = collectedClaim.value ?: return EvaluationValue.NULL_VALUE
        return EvaluationValue.stringValue(value.toString())
    }

    companion object {
        const val FUNCTION_NAME = "CLAIM"
    }
}

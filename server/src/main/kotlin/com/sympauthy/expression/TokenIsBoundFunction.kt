package com.sympauthy.expression

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.parser.Token

/**
 * Custom function checking whether the credential is a token bound to a key. Return true or false.
 */
class TokenIsBoundFunction(
    private val bound: Boolean = false
) : AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue = EvaluationValue.booleanValue(bound)

    companion object {
        const val FUNCTION_NAME = "TOKEN_IS_BOUND"
    }
}

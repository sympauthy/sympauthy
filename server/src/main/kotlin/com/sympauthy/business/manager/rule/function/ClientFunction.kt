package com.sympauthy.business.manager.rule.function

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import com.sympauthy.business.model.client.Client

/**
 * Custom function returning the value of a client property as a string.
 * Used in client scope granting rule expressions.
 */
@FunctionParameter(name = "property")
class ClientFunction(
    private val client: Client? = null
) : AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        if (parameterValues.isEmpty()) {
            return EvaluationValue.NULL_VALUE
        }
        val property = parameterValues[0].stringValue
        val client = this.client ?: return EvaluationValue.NULL_VALUE
        return when (property) {
            "id" -> EvaluationValue.stringValue(client.id)
            "public" -> EvaluationValue.booleanValue(client.public)
            else -> EvaluationValue.NULL_VALUE
        }
    }

    companion object {
        const val FUNCTION_NAME = "CLIENT"
    }
}

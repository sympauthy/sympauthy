package com.sympauthy.expression.manager

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token
import com.sympauthy.business.client.model.Client

/**
 * Custom function returning the value of a client property: `public` as a boolean, every other one as a
 * string, and null for a property the client does not have.
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
            "id", "client_id" -> EvaluationValue.stringValue(client.id)
            "public" -> EvaluationValue.booleanValue(client.public)
            "audience" -> EvaluationValue.stringValue(client.audience.id)
            else -> EvaluationValue.NULL_VALUE
        }
    }

    companion object {
        const val FUNCTION_NAME = "CLIENT"
    }
}

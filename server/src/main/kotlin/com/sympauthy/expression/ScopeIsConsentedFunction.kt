package com.sympauthy.expression

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token

/**
 * Custom function checking whether the person consented to a scope, for the audience the expression is
 * evaluated for. Return true or false.
 */
@FunctionParameter(name = "scope")
class ScopeIsConsentedFunction(
    /**
     * Scopes the person consented to.
     */
    private val consentedScopes: Collection<String> = emptyList()
) : AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val scope = parameterValues[0].stringValue
        return EvaluationValue.booleanValue(scope in consentedScopes)
    }

    companion object {
        const val FUNCTION_NAME = "SCOPE_IS_CONSENTED"
    }
}

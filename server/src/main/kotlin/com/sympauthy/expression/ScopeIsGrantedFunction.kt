package com.sympauthy.expression

import com.ezylang.evalex.Expression
import com.ezylang.evalex.data.EvaluationValue
import com.ezylang.evalex.functions.AbstractFunction
import com.ezylang.evalex.functions.FunctionParameter
import com.ezylang.evalex.parser.Token

/**
 * Custom function checking whether a rule granted a scope to the credential: a client scope on a
 * `client_credentials` token, a grantable scope on a person's. Return true or false.
 *
 * It never answers for a scope the person consented to, so a door opened to a granted scope is opened to
 * what the deployment decided and never to what a client asked for.
 */
@FunctionParameter(name = "scope")
class ScopeIsGrantedFunction(
    /**
     * Scopes a rule granted to the credential.
     */
    private val grantedScopes: Collection<String> = emptyList()
) : AbstractFunction() {

    override fun evaluate(
        expression: Expression,
        functionToken: Token,
        vararg parameterValues: EvaluationValue
    ): EvaluationValue {
        val scope = parameterValues[0].stringValue
        return EvaluationValue.booleanValue(scope in grantedScopes)
    }

    companion object {
        const val FUNCTION_NAME = "SCOPE_IS_GRANTED"
    }
}

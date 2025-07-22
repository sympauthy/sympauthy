package com.sympauthy.business.manager.rule

import com.ezylang.evalex.Expression
import jakarta.inject.Singleton

/**
 * This factory is in charge of parsing and validation expression used in configured scope granting rules.
 *
 * SympAuthy relies on [EvalEx](https://github.com/ezylang/EvalEx) as its expression evaluator.
 * It provides some handy custom functions to help user to access info related to the current authentication flow
 * (ex. scopes, claims, etc.).
 */
@Singleton
class ScopeGrantingRuleExpressionParser {

    fun parseExpression(expressionString: String): Expression {
        return Expression(expressionString)
    }
}

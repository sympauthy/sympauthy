package com.sympauthy.expression.manager

import com.ezylang.evalex.EvaluationException
import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue.DataType.BOOLEAN
import com.ezylang.evalex.parser.ParseException
import com.sympauthy.expression.exception.InvalidRuleExpressionException

/**
 * A configuration carrying none of the custom functions.
 *
 * Built again on every call, for the reason `docs/standards/expression-standard.md` gives.
 */
internal fun defaultExpressionConfiguration(): ExpressionConfiguration =
    ExpressionConfiguration.defaultConfiguration()

/**
 * Evaluate [expressionString] against [configuration] and return what it answers.
 *
 * An expression that does not parse, or that calls a function refusing what it was given, throws an
 * [InvalidRuleExpressionException] carrying `config.expression.invalid`, and one that parses but answers
 * something other than a boolean throws the same exception carrying `config.expression.invalid_return`.
 */
fun evaluateRuleExpression(
    expressionString: String,
    configuration: ExpressionConfiguration
): Boolean {
    val value = try {
        Expression(expressionString, configuration).evaluate()
    } catch (e: ParseException) {
        throw InvalidRuleExpressionException(
            expressionString = expressionString,
            configMessageId = "config.expression.invalid",
            businessErrorDetailsId = "expression.evaluate.failed",
            parseError = e.message,
        )
    } catch (e: EvaluationException) {
        throw InvalidRuleExpressionException(
            expressionString = expressionString,
            configMessageId = "config.expression.invalid",
            businessErrorDetailsId = "expression.evaluate.failed",
            parseError = e.message,
            cause = e,
        )
    }
    if (value.dataType != BOOLEAN) {
        throw InvalidRuleExpressionException(
            expressionString = expressionString,
            configMessageId = "config.expression.invalid_return",
            businessErrorDetailsId = "expression.evaluate.invalid_return",
        )
    }
    return value.booleanValue
}

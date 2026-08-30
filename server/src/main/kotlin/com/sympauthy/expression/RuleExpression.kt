package com.sympauthy.expression

import com.ezylang.evalex.Expression
import com.ezylang.evalex.config.ExpressionConfiguration
import com.ezylang.evalex.data.EvaluationValue.DataType.BOOLEAN
import com.ezylang.evalex.parser.ParseException

/**
 * A rule expression that will not evaluate to a boolean, either because it does not parse or because
 * it returns something else.
 *
 * It carries two error codes because the same failure is reported to two audiences at two moments: to
 * an operator when their configuration is read, and as an internal failure when an expression that
 * passed that reading somehow fails later.
 */
class InvalidRuleExpressionException(
    val expressionString: String,
    /**
     * Code reported when the expression is refused while the deployment's configuration is being read.
     */
    val configMessageId: String,
    /**
     * Code reported when the expression is refused while a request is being served.
     */
    val businessErrorDetailsId: String,
    parseError: String? = null,
    /**
     * The expression and what was wrong with it, as the message each of the two codes names
     * interpolates it.
     *
     * It carries the same text as [message] and exists because that one is [Throwable]'s, and so
     * nullable.
     */
    val reason: String = listOfNotNull(expressionString, parseError).joinToString(" - ")
) : Exception(reason)

/**
 * A configuration carrying none of the custom functions.
 *
 * Built again on every call, and it has to be: EvalEx registers a function by mutating the
 * configuration it is asked to add it to and handing that same instance back, so one kept between
 * calls would let each request rebind the functions every other request is evaluating against.
 */
internal fun defaultExpressionConfiguration(): ExpressionConfiguration =
    ExpressionConfiguration.defaultConfiguration()

/**
 * Evaluate [expressionString] against [configuration] and return what it answers.
 *
 * An expression that does not parse throws an [InvalidRuleExpressionException] carrying
 * `config.rule.expression.invalid`, and one that parses but answers something other than a boolean
 * throws the same exception carrying `config.rule.expression.invalid_return`.
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
            configMessageId = "config.rule.expression.invalid",
            businessErrorDetailsId = "rule.evaluate.failed",
            parseError = e.message,
        )
    }
    if (value.dataType != BOOLEAN) {
        throw InvalidRuleExpressionException(
            expressionString = expressionString,
            configMessageId = "config.rule.expression.invalid_return",
            businessErrorDetailsId = "rule.evaluate.invalid_return",
        )
    }
    return value.booleanValue
}

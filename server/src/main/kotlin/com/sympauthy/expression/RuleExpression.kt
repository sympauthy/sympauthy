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
    message: String? = null
) : Exception("$expressionString - $message")

/**
 * The expression configuration carrying none of the custom functions.
 */
internal val defaultExpressionConfiguration: ExpressionConfiguration by lazy {
    ExpressionConfiguration.defaultConfiguration()
}

/**
 * Evaluate [expressionString] against [configuration].
 *
 * @throws InvalidRuleExpressionException config.rule.expression.invalid when it does not parse, and
 * config.rule.expression.invalid_return when it evaluates to something that is not a boolean.
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
            message = e.message,
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

package com.sympauthy.expression.exception

/**
 * A rule expression that will not evaluate to a boolean, either because it does not parse or because
 * it returns something else.
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
    cause: Throwable? = null
) : Exception(formatMessage(expressionString, parseError), cause) {

    /**
     * The expression and what was wrong with it, as the message each of the two codes names
     * interpolates it.
     *
     * It carries the same text as [message] and exists because that one is [Throwable]'s, and so
     * nullable.
     */
    val reason: String = formatMessage(expressionString, parseError)

    companion object {
        private fun formatMessage(expressionString: String, parseError: String?): String {
            return listOfNotNull(expressionString, parseError).joinToString(" - ")
        }
    }
}

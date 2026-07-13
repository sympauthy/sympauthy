package com.sympauthy.business.manager.actas

class InvalidActAsRuleException(
    val expressionString: String,
    /**
     * The configuration error message identifier to use if the evaluation fails during the validation of the
     * configuration.
     */
    val configMessageId: String,
    /**
     * The business error message identifier to use if the evaluation fails during evaluation of the expression for a
     * token exchange request.
     */
    val businessErrorDetailsId: String,
    message: String? = null
) : Exception("$expressionString - $message")

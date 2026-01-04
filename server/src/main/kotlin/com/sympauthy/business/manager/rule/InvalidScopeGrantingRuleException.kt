package com.sympauthy.business.manager.rule

class InvalidScopeGrantingRuleException(
    expressionString: String,
    message: String?
) : Exception("$expressionString - $message")

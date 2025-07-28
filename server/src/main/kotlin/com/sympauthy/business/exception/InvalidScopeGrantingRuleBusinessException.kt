package com.sympauthy.business.exception

class InvalidScopeGrantingRuleBusinessException(
    val expressionString: String,
):  BusinessException(
    detailsId = ""
) {
}
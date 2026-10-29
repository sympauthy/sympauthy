package com.sympauthy.api.controller.oauth2.util

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST

/**
 * [value], the form parameter named [param] of a request to an endpoint of `/api/oauth2`.
 *
 * Throws an `OAuth2Exception` carrying `invalid_request` and `token.missing_param`, naming [param], where
 * [value] is missing or blank.
 */
fun getRequiredParam(value: String?, param: String): String {
    if (value.isNullOrBlank()) {
        throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to param)
    }
    return value
}

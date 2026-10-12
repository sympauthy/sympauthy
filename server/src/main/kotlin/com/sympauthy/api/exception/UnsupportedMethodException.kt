package com.sympauthy.api.exception

import io.micronaut.http.HttpHeaders.ALLOW
import io.micronaut.http.HttpStatus.METHOD_NOT_ALLOWED

/**
 * A request refused because the route it names answers no request made with its method, answered with
 * the `Allow` list RFC 9110 §15.5.6 requires of a `405`.
 */
class UnsupportedMethodException(
    method: String,
    /**
     * The methods the route answers.
     */
    private val allowedMethods: Collection<String>
) : LocalizedHttpException(
    status = METHOD_NOT_ALLOWED,
    recoverable = true,
    detailsId = "method.unsupported",
    descriptionId = "description.method.unsupported",
    values = mapOf(
        "method" to method,
        "allowedMethods" to allowedMethods.joinToString(", ")
    )
) {

    override val headers: Map<String, String>
        get() = mapOf(ALLOW to allowedMethods.joinToString(", "))
}

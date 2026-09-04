package com.sympauthy.api.util

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.util.wireName
import io.micronaut.http.HttpStatus.BAD_REQUEST

/**
 * Resolve the filter query parameter [name], sent as [value], into the value of [T] it names, or
 * null where the caller left it out.
 *
 * A [value] naming no member of [T] — compared against the name [publishedName] gives it, ignoring
 * case — is refused as `filter.value.unsupported`, a `400` naming the parameter, what was sent and
 * what the set admits. A caller asking for something this deployment cannot have is told so, rather
 * than handed a page that reads as a deployment holding none of it.
 *
 * [publishedName] defaults to the [wireName] every enum is published under, and is written out
 * where a value publishes another one.
 */
inline fun <reified T : Enum<T>> filterOf(
    name: String,
    value: String?,
    crossinline publishedName: (T) -> String = { it.wireName }
): T? {
    if (value == null) return null
    return enumValues<T>().firstOrNull { publishedName(it).equals(value, ignoreCase = true) }
        ?: throw httpExceptionOf(
            BAD_REQUEST, "filter.value.unsupported", "description.filter.value.unsupported",
            "parameter" to name,
            "value" to value,
            "supportedValues" to enumValues<T>().joinToString(", ") { publishedName(it) }
        )
}

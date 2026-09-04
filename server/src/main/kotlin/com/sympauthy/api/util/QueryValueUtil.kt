package com.sympauthy.api.util

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.model.page.SortOrder
import com.sympauthy.util.wireName
import io.micronaut.http.HttpStatus.BAD_REQUEST

/**
 * Resolve the query parameter [name], sent as [value], into the value of [T] it names, or null where
 * the caller left it out.
 *
 * A [value] naming no member of [T] — compared against the name [publishedName] gives it, ignoring
 * case — is refused as [detailsId], a `400` naming the parameter, what was sent and what the set
 * admits. A caller asking for something this deployment cannot have is told so, rather than handed a
 * page that reads as a deployment holding none of it.
 *
 * What the parameter asks for decides the code: [filterOf] and [orderOf] each name their own, since
 * the description a caller reads says what the parameter they got wrong is.
 */
inline fun <reified T : Enum<T>> queryValueOf(
    name: String,
    value: String?,
    detailsId: String,
    descriptionId: String,
    crossinline publishedName: (T) -> String
): T? {
    if (value == null) return null
    return enumValues<T>().firstOrNull { publishedName(it).equals(value, ignoreCase = true) }
        ?: throw httpExceptionOf(
            BAD_REQUEST, detailsId, descriptionId,
            "parameter" to name,
            "value" to value,
            "supportedValues" to enumValues<T>().joinToString(", ") { publishedName(it) }
        )
}

/**
 * Resolve the filter query parameter [name], sent as [value], into the value of [T] it names, or
 * null where the caller left it out, refusing a value naming no member of [T] as
 * `filter.value.unsupported`.
 *
 * [publishedName] defaults to the [wireName] every enum is published under, and is written out
 * where a value publishes another one.
 */
inline fun <reified T : Enum<T>> filterOf(
    name: String,
    value: String?,
    crossinline publishedName: (T) -> String = { it.wireName }
): T? = queryValueOf(
    name = name,
    value = value,
    detailsId = "filter.value.unsupported",
    descriptionId = "description.filter.value.unsupported",
    publishedName = publishedName
)

/**
 * Resolve the sort direction query parameter [name], sent as [value], into the [SortOrder] it names,
 * or null where the caller left it out and takes the direction the listing sorts in by default.
 *
 * A [value] naming neither direction is refused as `order.value.unsupported`. An ordering is not a
 * filter, and the description a caller reads is written for a person: it says which parameter they
 * got wrong, so it cannot call their sort direction a filter.
 */
fun orderOf(
    name: String,
    value: String?
): SortOrder? = queryValueOf<SortOrder>(
    name = name,
    value = value,
    detailsId = "order.value.unsupported",
    descriptionId = "description.order.value.unsupported"
) { it.wireName }

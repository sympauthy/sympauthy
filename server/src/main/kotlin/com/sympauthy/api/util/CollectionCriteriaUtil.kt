package com.sympauthy.api.util

import com.sympauthy.api.exception.recoverableHttpExceptionOf
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionCriterion
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.CollectionOperator.CONTAINS
import com.sympauthy.business.model.collection.CollectionOperator.EQ
import com.sympauthy.business.model.collection.CollectionOperator.IN
import com.sympauthy.business.model.collection.CollectionOperator.IS_NULL
import com.sympauthy.business.model.collection.CollectionOperator.STARTS_WITH
import com.sympauthy.business.model.collection.CollectionSortKey
import com.sympauthy.util.wireName
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus.BAD_REQUEST

/**
 * The parameters every collection reads for something other than a criterion.
 */
private val RESERVED_PARAMETERS = setOf("page", "size", "sort", "q")

/**
 * The name the criteria are declared under in the published specification.
 *
 * It is never sent: the parameter is an object serialized the way a query string spells one, so what
 * reaches the server is the criteria it holds, each under the name of the field it narrows. It is the
 * word the capability document already publishes them under.
 */
const val FILTER_PARAMETER_NAME = "filter"

/**
 * What the published specification says about each parameter the grammar fixes, on every collection
 * that binds one.
 *
 * Each rule is on the parameter it governs, where a caller writing a query string reads it, and each
 * is written once here rather than per controller. What is particular to one collection — the fields
 * it searches, where it publishes what it offers — is on that collection's own operation.
 */
const val PAGE_PARAMETER_DESCRIPTION = "Zero-indexed page number. A page outside the bounds of this " +
    "collection is refused rather than clamped."

/**
 * @see PAGE_PARAMETER_DESCRIPTION
 */
const val SIZE_PARAMETER_DESCRIPTION = "Number of results per page. Defaults to the size this server " +
    "is configured with and may not exceed its configured maximum; a size outside those bounds is " +
    "refused rather than clamped."

/**
 * A criterion names a field the collection declared, so there is no parameter a specification could
 * enumerate; one object-typed parameter is how OpenAPI says that a query string carries names it does
 * not know ahead of the request.
 *
 * @see PAGE_PARAMETER_DESCRIPTION
 */
const val FILTER_PARAMETER_DESCRIPTION = "Criteria narrowing this collection, each sent as a query " +
    "parameter of its own — this parameter is never sent under its own name. A bare `field=value` " +
    "is an exact match, and every other operator is a dotted suffix on the field name: `.eq` `.ne`, " +
    "`.lt` `.lte` `.gt` `.gte`, `.in` over a comma-separated list, `.contains` `.starts_with` " +
    "partially and ignoring case, and `.is_null=true` for a row carrying no value at all. Which of " +
    "them a field admits follows from its type. Criteria compose with `and` — `created_at.gte` " +
    "beside `created_at.lte` is a range — and there is no `or` and no grouping. A field this " +
    "collection does not offer, an operator it does not admit and a value its set does not hold are " +
    "each a 400 naming the parameter. Example: " +
    "`status=enabled&email.contains=ana&created_at.gte=2026-01-01T00:00:00`."

/**
 * @see PAGE_PARAMETER_DESCRIPTION
 */
const val SORT_PARAMETER_DESCRIPTION = "Comma-separated list of keys to order by, read left to " +
    "right, each descending when prefixed with `-`: `sort=-created_at,email` reads the newest first " +
    "and settles ties by email. A key this collection does not order on is refused, and the order " +
    "ends on a key that is unique whichever direction is asked for."

/**
 * @see PAGE_PARAMETER_DESCRIPTION
 */
const val SEARCH_PARAMETER_DESCRIPTION = "Partial, case-insensitive match across the fields this " +
    "collection searches. It narrows what the criteria kept rather than widening it."

/**
 * Read everything [request] asks of a collection beyond the page it wants, against the [capabilities]
 * that collection declared.
 *
 * [sort] and [query] are the two the grammar fixes, so the controller binds them and hands them
 * over; a collection that offers neither binds neither, and what arrived is then read off [request] so
 * that it is refused rather than ignored. Every other query parameter that is not reserved is a
 * criterion, because a collection's fields are its own and nothing can declare them as parameters —
 * which is also why a parameter naming none of them is refused rather than ignored, so a caller who
 * mistyped is told.
 *
 * A parameter the collection reads for something else — `/admin/users`'s `claims`, which selects what
 * is published rather than what is kept, is one — is named in [reservedParameters] instead of being
 * read as a criterion.
 *
 * What is refused, and under which code, is docs/collection-standard.md.
 */
fun collectionCriteriaOf(
    request: HttpRequest<*>,
    capabilities: CollectionCapabilities,
    sort: String? = null,
    query: String? = null,
    reservedParameters: Set<String> = emptySet()
): CollectionCriteria {
    val reserved = RESERVED_PARAMETERS + reservedParameters
    val filters = request.parameters.asMap()
        .filterKeys { it !in reserved }
        .flatMap { (parameter, values) -> values.map { criterionOf(capabilities, parameter, it) } }
    return CollectionCriteria(
        filters = filters,
        sort = sortOf(capabilities, sort ?: request.parameters["sort"]),
        query = queryOf(capabilities, query ?: request.parameters["q"])
    )
}

/**
 * The criterion [parameter], sent as [value], names.
 *
 * A parameter naming a field outright is an exact match, which is what every filter this server
 * shipped before the grammar already meant. Anything else is a field and an operator, split at the
 * last dot — which is that way around so that a field whose own name holds one is still found.
 */
private fun criterionOf(
    capabilities: CollectionCapabilities,
    parameter: String,
    value: String
): CollectionCriterion {
    val exact = capabilities.filterFieldOrNull(parameter)
    if (exact != null) {
        return criterionOf(parameter, exact, EQ, value)
    }

    val separator = parameter.lastIndexOf('.')
    if (separator <= 0) throw unknownField(parameter, capabilities)
    val field = capabilities.filterFieldOrNull(parameter.take(separator))
        ?: throw unknownField(parameter, capabilities)

    val suffix = parameter.substring(separator + 1)
    val operator = CollectionOperator.entries.firstOrNull { it.wireName == suffix }
        ?.takeIf { it in field.operators }
        ?: throw unsupportedOperator(parameter, field, suffix)

    return criterionOf(parameter, field, operator, value)
}

private fun criterionOf(
    parameter: String,
    field: CollectionField,
    operator: CollectionOperator,
    value: String
): CollectionCriterion {
    if (operator !in field.operators) throw unsupportedOperator(parameter, field, operator.wireName)
    return CollectionCriterion(
        field = field,
        operator = operator,
        values = valuesOf(parameter, field, operator, value)
    )
}

/**
 * The value or values [operator] was sent with, each read as [field] says it is written.
 *
 * `is_null` asks a question about the row rather than about a value, so it takes a boolean whatever
 * the field holds; `contains` and `starts_with` match a fragment, which is never a whole value and
 * is therefore not read as one.
 */
private fun valuesOf(
    parameter: String,
    field: CollectionField,
    operator: CollectionOperator,
    value: String
): List<Comparable<*>> = when (operator) {
    IS_NULL -> listOf(
        value.lowercase().toBooleanStrictOrNull() ?: throw malformedValue(parameter, "boolean", value)
    )

    CONTAINS, STARTS_WITH -> listOf(value)

    IN -> value.split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .ifEmpty { throw malformedValue(parameter, field.type.wireName, value) }
        .map { valueOf(parameter, field, it) }

    else -> listOf(valueOf(parameter, field, value))
}

/**
 * [value] as [field] holds it: the spelling its set holds where the set is closed, then read as its
 * type.
 *
 * The closed set is matched ignoring case and answered as it spells the value, so what reaches a
 * manager is the configured spelling rather than the one that arrived.
 */
private fun valueOf(
    parameter: String,
    field: CollectionField,
    value: String
): Comparable<*> {
    val word = when (field.values) {
        null -> value
        else -> field.valueOrNull(value) ?: throw unsupportedValue(parameter, field, value)
    }
    return field.type.normalizeOrNull(word) ?: throw malformedValue(parameter, field.type.wireName, value)
}

/**
 * The keys [sort] names, or the collection's own order where the caller named none.
 */
private fun sortOf(
    capabilities: CollectionCapabilities,
    sort: String?
): List<CollectionSortKey> {
    if (sort.isNullOrBlank()) return capabilities.defaultSort
    return sort.split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { key ->
            val name = key.removePrefix("-")
            val field = capabilities.sortFields.firstOrNull { it.name == name }
                ?: throw unknownSortField(name, capabilities)
            CollectionSortKey(field, descending = key.startsWith("-"))
        }
}

/**
 * The free text [query] carries, refused where the collection names no field to match it against.
 *
 * A caller who sent one anyway asked for something that cannot be answered, which is a different
 * answer from a page narrowed by nothing.
 */
private fun queryOf(
    capabilities: CollectionCapabilities,
    query: String?
): String? {
    if (query != null && capabilities.searchFields.isEmpty()) {
        throw recoverableHttpExceptionOf(
            BAD_REQUEST, "collection.search.unsupported", "description.collection.search.unsupported",
            "parameter" to "q"
        )
    }
    return query
}

private fun unknownField(parameter: String, capabilities: CollectionCapabilities) =
    recoverableHttpExceptionOf(
        BAD_REQUEST, "collection.filter.unknown_field", "description.collection.filter.unknown_field",
        "parameter" to parameter,
        "supportedFields" to capabilities.filterFields.joinToString(", ", transform = CollectionField::name)
    )

private fun unsupportedOperator(parameter: String, field: CollectionField, operator: String) =
    recoverableHttpExceptionOf(
        BAD_REQUEST,
        "collection.filter.unsupported_operator",
        "description.collection.filter.unsupported_operator",
        "parameter" to parameter,
        "field" to field.name,
        "operator" to operator,
        "supportedOperators" to field.operators.joinToString(", ") { it.wireName }
    )

private fun unsupportedValue(parameter: String, field: CollectionField, value: String) =
    recoverableHttpExceptionOf(
        BAD_REQUEST, "collection.filter.value.unsupported", "description.collection.filter.value.unsupported",
        "parameter" to parameter,
        "value" to value,
        "supportedValues" to field.values.orEmpty().joinToString(", ") { it.value }
    )

private fun malformedValue(parameter: String, type: String, value: String) =
    recoverableHttpExceptionOf(
        BAD_REQUEST, "collection.filter.value.malformed", "description.collection.filter.value.malformed",
        "parameter" to parameter,
        "value" to value,
        "type" to type
    )

private fun unknownSortField(name: String, capabilities: CollectionCapabilities) =
    recoverableHttpExceptionOf(
        BAD_REQUEST, "collection.sort.unknown_field", "description.collection.sort.unknown_field",
        "parameter" to "sort",
        "field" to name,
        "supportedFields" to capabilities.sortFields.joinToString(", ", transform = CollectionField::name)
    )

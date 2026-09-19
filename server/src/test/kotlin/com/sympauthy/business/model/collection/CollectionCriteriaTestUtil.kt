package com.sympauthy.business.model.collection

/**
 * The criteria a caller writing [filters], [sort] and [query] reaches a manager with, resolved
 * against the fields this collection declared.
 *
 * A filter is written the way it is sent: the field name, an operator appended after a dot where it
 * is not an exact match, and the value as a caller would spell it. What the `api` layer does on top
 * of this is refuse what the fields do not admit, which is tested where that is written.
 */
fun CollectionCapabilities.criteriaOf(
    vararg filters: Pair<String, String>,
    sort: String? = null,
    query: String? = null
): CollectionCriteria = CollectionCriteria(
    filters = filters.map { (parameter, value) -> criterionOf(parameter, value) },
    sort = sort?.split(",")?.map { key ->
        CollectionSortKey(fieldOf(key.removePrefix("-")), descending = key.startsWith("-"))
    } ?: defaultSort,
    query = query
)

private fun CollectionCapabilities.criterionOf(parameter: String, value: String): CollectionCriterion {
    val separator = parameter.lastIndexOf('.')
    val named = if (separator > 0) {
        CollectionOperator.entries.firstOrNull { it.name.lowercase() == parameter.substring(separator + 1) }
    } else {
        null
    }
    val field = fieldOf(if (named == null) parameter else parameter.take(separator))
    return CollectionCriterion(
        field = field,
        operator = named ?: CollectionOperator.EQ,
        values = when (named) {
            CollectionOperator.IS_NULL -> listOf(value.toBooleanStrict())
            CollectionOperator.CONTAINS, CollectionOperator.STARTS_WITH -> listOf(value)
            CollectionOperator.IN -> value.split(",").map { field.valueOf(it) }
            else -> listOf(field.valueOf(value))
        }
    )
}

private fun CollectionCapabilities.fieldOf(name: String): CollectionField =
    fieldOrNull(name) ?: error("This collection declares no field named $name.")

private fun CollectionField.valueOf(value: String): Comparable<*> =
    type.normalizeOrNull(valueOrNull(value) ?: value) ?: error("$value is not a $name.")

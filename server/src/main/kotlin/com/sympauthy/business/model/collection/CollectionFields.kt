package com.sympauthy.business.model.collection

import com.sympauthy.business.model.collection.CollectionOperator.CONTAINS
import com.sympauthy.business.model.collection.CollectionOperator.EQ
import com.sympauthy.business.model.collection.CollectionOperator.GT
import com.sympauthy.business.model.collection.CollectionOperator.GTE
import com.sympauthy.business.model.collection.CollectionOperator.IN
import com.sympauthy.business.model.collection.CollectionOperator.IS_NULL
import com.sympauthy.business.model.collection.CollectionOperator.LT
import com.sympauthy.business.model.collection.CollectionOperator.LTE
import com.sympauthy.business.model.collection.CollectionOperator.NE
import com.sympauthy.business.model.collection.CollectionOperator.STARTS_WITH
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.orderedPage
import com.sympauthy.util.wireName

/**
 * The fields of one collection, each bound to the value it reads off a row.
 *
 * [capabilities] is what the collection publishes and what a caller's query string is resolved against;
 * the readers are what turns the criteria that come back into a page. One evaluator therefore
 * filters, searches and orders every collection that holds its rows in memory, and a collection that adds
 * a field gets all of it.
 *
 * A collection whose criteria reach the database declares the same fields and reads
 * [CollectionCriteria] itself, so it publishes a capability document without pretending its rows are
 * here.
 */
class CollectionFields<T> internal constructor(
    val capabilities: CollectionCapabilities,
    private val readers: Map<String, (T) -> Any?>,
    private val uniqueKey: Comparator<T>
) {

    /**
     * The page [pageParams] names of the [items] [criteria] keep, in the order they asked for.
     *
     * The order ends on the key this collection is unique by, ascending, whatever the caller asked:
     * that is what decides what their own keys leave undecided and what makes a walk through the
     * pages total.
     */
    fun page(
        items: List<T>,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<T> = items
        .filter { keeps(it, criteria) }
        .orderedPage(pageParams, comparatorOf(criteria))

    /**
     * Whether [item] passes every criterion and the free text [criteria] carries.
     */
    internal fun keeps(item: T, criteria: CollectionCriteria): Boolean {
        if (criteria.filters.any { !matches(item, it) }) return false
        val query = criteria.query?.takeIf(String::isNotBlank) ?: return true
        val lowered = query.lowercase()
        return capabilities.searchFields.any { field ->
            valuesOf(item, field).any { it.toString().lowercase().contains(lowered) }
        }
    }

    /**
     * The order a page is read in: the caller's keys, then the key this collection is unique by.
     */
    internal fun comparatorOf(criteria: CollectionCriteria): Comparator<T> = criteria.sort
        .fold(Comparator<T> { _, _ -> 0 }) { order, key ->
            val byKey = Comparator<T> { left, right -> compareOn(key.field, left, right) }
            order.then(if (key.descending) byKey.reversed() else byKey)
        }
        .then(uniqueKey)

    private fun matches(item: T, criterion: CollectionCriterion): Boolean {
        val field = criterion.field
        val read = valuesOf(item, field)
        val sent = criterion.values.first()
        return when (criterion.operator) {
            IS_NULL -> read.isEmpty() == (sent == true)
            EQ -> read.any { holds(field, it, sent) }
            NE -> read.none { holds(field, it, sent) }
            IN -> read.any { value -> criterion.values.any { holds(field, value, it) } }
            CONTAINS -> read.any { it.toString().lowercase().contains(sent.toString().lowercase()) }
            STARTS_WITH -> read.any { it.toString().lowercase().startsWith(sent.toString().lowercase()) }
            LT, LTE, GT, GTE -> read.any { satisfies(criterion.operator, comparisonOf(field, it, sent)) }
        }
    }

    private fun satisfies(operator: CollectionOperator, comparison: Int?): Boolean = when {
        comparison == null -> false
        operator == LT -> comparison < 0
        operator == LTE -> comparison <= 0
        operator == GT -> comparison > 0
        else -> comparison >= 0
    }

    private fun holds(field: CollectionField, read: Any, sent: Comparable<*>): Boolean =
        field.type.normalizeOrNull(read) == sent

    private fun comparisonOf(field: CollectionField, read: Any, sent: Comparable<*>): Int? =
        field.type.normalizeOrNull(read)?.let { compareNormalized(it, sent) }

    private fun compareOn(field: CollectionField, left: T, right: T): Int {
        val leftValue = sortValueOf(field, left)
        val rightValue = sortValueOf(field, right)
        return when {
            leftValue == null && rightValue == null -> 0
            leftValue == null -> 1
            rightValue == null -> -1
            else -> compareNormalized(leftValue, rightValue)
        }
    }

    /**
     * The value [field] is ordered on for [item], which is the smallest where the field reads
     * several. A row carrying none sorts last, and `sort=-` therefore sorts it first.
     */
    private fun sortValueOf(field: CollectionField, item: T): Comparable<*>? = valuesOf(item, field)
        .mapNotNull { field.type.normalizeOrNull(it) }
        .minWithOrNull { left, right -> compareNormalized(left, right) }

    /**
     * The values [field] reads off [item]: none where the row carries nothing, and several where the
     * field reads a collection — a user's claims, the places a session was driven from.
     */
    private fun valuesOf(item: T, field: CollectionField): List<Any> =
        when (val read = readers[field.name]?.invoke(item)) {
            null -> emptyList()
            is Collection<*> -> read.filterNotNull()
            else -> listOf(read)
        }
}

/**
 * Compare two values [CollectionFieldType.normalizeOrNull] answered for one field, which are of one
 * type by construction — the cast is what the signature of [Comparable] costs and nothing else.
 */
@Suppress("UNCHECKED_CAST")
private fun compareNormalized(left: Comparable<*>, right: Comparable<*>): Int =
    if (left.javaClass == right.javaClass) (left as Comparable<Any>).compareTo(right) else 0

/**
 * Declare the fields of one collection, ordered by [uniqueKey] once the caller's own keys have decided
 * what they can.
 *
 * [uniqueKey] must leave no two rows equal: it is what the pages are walked on, and two rows it ties
 * are free to swap between two calls.
 *
 * It suspends because a field over a set this deployment configured reads that set from the manager
 * that owns it, and some of those reads do.
 */
suspend fun <T> collectionFields(
    uniqueKey: Comparator<T>,
    declare: suspend CollectionFieldsBuilder<T>.() -> Unit
): CollectionFields<T> {
    val builder = CollectionFieldsBuilder<T>()
    builder.declare()
    return builder.build(uniqueKey)
}

/**
 * Collects the fields of one collection as it declares them.
 */
class CollectionFieldsBuilder<T> {

    private val declared = mutableListOf<CollectionField>()
    private val readers = mutableMapOf<String, (T) -> Any?>()
    private var defaultSort = emptyList<CollectionSortKey>()

    /**
     * Declare the field [name], holding a value of [type] that [read] answers for a row.
     *
     * [key] is the identifier the field's name and its values are displayed under, and it defaults to
     * the field's own; a field that is something this deployment configured names that thing's key
     * instead. [nullable] is what adds `is_null` to the operators, and [operators] narrows the set
     * [type] admits — a field the collection does not filter on passes an empty one.
     */
    fun field(
        name: String,
        type: CollectionFieldType,
        key: String = "fields.$name",
        values: List<CollectionFieldValue>? = null,
        nullable: Boolean = false,
        sortable: Boolean = false,
        searchable: Boolean = false,
        operators: Set<CollectionOperator> = type.operators,
        read: (T) -> Any?
    ) {
        // A name declared twice would publish two entries backed by whichever reader came last, so a
        // collection whose fields are this deployment's says what it left out instead.
        require(name !in readers) { "This collection declares the field $name twice." }
        declared += CollectionField(
            name = name,
            type = type,
            key = key,
            operators = if (nullable && operators.isNotEmpty()) operators + IS_NULL else operators,
            values = values,
            sortable = sortable,
            searchable = searchable
        )
        readers[name] = read
    }

    /**
     * Declare the field [name] over the values [E] publishes, which [read] answers for a row.
     *
     * The values are the enum's own, each named under [key] and the word it is published as, so a
     * value spelled differently on purpose is spelled that way here too.
     */
    inline fun <reified E : Enum<E>> enumeration(
        name: String,
        key: String = "fields.$name",
        nullable: Boolean = false,
        sortable: Boolean = false,
        searchable: Boolean = false,
        noinline read: (T) -> E?
    ) = field(
        name = name,
        type = CollectionFieldType.ENUM,
        key = key,
        values = enumFieldValues<E>(key),
        nullable = nullable,
        sortable = sortable,
        searchable = searchable,
        read = { read(it)?.wireName }
    )

    /**
     * The order this collection reads in where the caller named none, spelled the way `sort` is.
     *
     * A collection whose own order is the unique key it ends on declares none: there is nothing to name
     * ahead of a key a caller may not name either.
     */
    fun defaultSort(vararg keys: String) {
        defaultSort = keys.map { key ->
            val name = key.removePrefix("-")
            val field = declared.firstOrNull { it.name == name && it.sortable }
                ?: error("This collection orders by $name by default and does not declare it as sortable.")
            CollectionSortKey(field, descending = key.startsWith("-"))
        }
    }

    internal fun build(uniqueKey: Comparator<T>) = CollectionFields(
        capabilities = CollectionCapabilities(declared.toList(), defaultSort),
        readers = readers.toMap(),
        uniqueKey = uniqueKey
    )
}

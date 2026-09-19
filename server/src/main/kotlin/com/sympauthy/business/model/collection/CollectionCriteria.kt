package com.sympauthy.business.model.collection

/**
 * Everything a caller asked of a collection beyond the page they wanted, once every word of it has been
 * read against what that collection declared.
 *
 * A manager is handed one of these rather than a parameter per criterion: the fields are the
 * collection's own and the grammar is not, so nothing between the query string and here has to be
 * written again for the next collection.
 */
data class CollectionCriteria(
    /**
     * The criteria the caller named, which compose with `and`.
     */
    val filters: List<CollectionCriterion>,
    /**
     * The keys the page is ordered by, read left to right, ahead of the unique key the collection
     * appends. It is the collection's own order where the caller named none.
     */
    val sort: List<CollectionSortKey>,
    /**
     * The free text the page is narrowed by, or null where the caller sent none.
     */
    val query: String?
) {

    /**
     * Every word the caller sent as an exact match on [field], in the order they sent them.
     *
     * It is for a collection whose criteria reach the database rather than the rows in memory,
     * declaring the fields it can answer under `eq` alone. **It answers a list because criteria
     * compose with `and`**: a caller naming one field twice asked for a row holding both values,
     * which is nothing rather than either of them, and reading only the first would hand them one
     * of the two as though they had asked for it.
     */
    fun exactValuesOf(field: String): List<String> = filters
        .filter { it.field.name == field && it.operator == CollectionOperator.EQ }
        .mapNotNull { it.values.firstOrNull() as? String }

    companion object {
        /**
         * A caller who asked for nothing but a page, which is what a collection offering no criterion
         * ever sees.
         */
        val NONE = CollectionCriteria(emptyList(), emptyList(), null)
    }
}

/**
 * One criterion: a field, what is asked of it, and the value or values that were sent.
 *
 * [values] holds one value for every operator but `in`, and each of them has already been read as
 * [CollectionField.type] says — so nothing downstream parses a word a caller wrote.
 */
data class CollectionCriterion(
    val field: CollectionField,
    val operator: CollectionOperator,
    val values: List<Comparable<*>>
) {
    init {
        require(values.isNotEmpty()) { "A criterion carries the value or values that were sent." }
    }
}

/**
 * One key of an order, and whether it is read from the largest value to the smallest.
 */
data class CollectionSortKey(
    val field: CollectionField,
    val descending: Boolean
) {

    /**
     * The key as a caller spells it in `sort`, the `-` included, which is how a collection publishes its
     * own order.
     *
     * The receiver is written out because `field` inside a property accessor is the backing field.
     */
    val spelling: String
        get() = if (descending) "-${this.field.name}" else this.field.name
}

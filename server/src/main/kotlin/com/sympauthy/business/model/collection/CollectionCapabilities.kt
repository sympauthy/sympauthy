package com.sympauthy.business.model.collection

/**
 * What one collection accepts: the fields it offers and the order it takes when a caller names none.
 *
 * It is what the capability document is rendered from and what a caller's query string is resolved
 * against, which is what keeps the two in agreement — a field absent here is one the collection refuses
 * and one present is admitted under every operator it lists.
 */
data class CollectionCapabilities(
    val fields: List<CollectionField>,
    /**
     * The order the collection reads in where the caller named none, ahead of the unique key the server
     * appends. It is empty where that unique key is already the whole of the order.
     */
    val defaultSort: List<CollectionSortKey>
) {

    /**
     * The fields a caller may filter on.
     */
    val filterFields: List<CollectionField>
        get() = fields.filter { it.operators.isNotEmpty() }

    /**
     * The fields a caller may order on.
     */
    val sortFields: List<CollectionField>
        get() = fields.filter(CollectionField::sortable)

    /**
     * The fields `q` matches against. A collection with none of them cannot answer a `q` at all.
     */
    val searchFields: List<CollectionField>
        get() = fields.filter(CollectionField::searchable)

    fun fieldOrNull(name: String): CollectionField? = fields.firstOrNull { it.name == name }

    /**
     * The field [name] a caller may filter on, or null where the collection declares none — a field it
     * only orders or searches on is as unknown to a filter as one it never declared.
     */
    fun filterFieldOrNull(name: String): CollectionField? =
        fieldOrNull(name)?.takeIf { it.operators.isNotEmpty() }
}

package com.sympauthy.business.model.collection

/**
 * What a criterion asks of the field it names, written as a dotted suffix on that field's name.
 *
 * The set is closed and every collection offers the same words, so what renders a filter bar holds them
 * rather than reading them from a capability document — which is why none of them is localized.
 */
enum class CollectionOperator {
    /** The value is exactly the one sent, which is also what a bare `field=` means. */
    EQ,

    /** The field carries no value equal to the one sent. */
    NE,

    /** The value is smaller than the one sent. */
    LT,

    /** The value is smaller than or equal to the one sent. */
    LTE,

    /** The value is greater than the one sent. */
    GT,

    /** The value is greater than or equal to the one sent. */
    GTE,

    /** The value is one of a comma-separated list. */
    IN,

    /** The value holds the one sent, ignoring case. */
    CONTAINS,

    /** The value begins with the one sent, ignoring case. */
    STARTS_WITH,

    /** The row carries no value for this field, or carries one where `false` was sent. */
    IS_NULL
}

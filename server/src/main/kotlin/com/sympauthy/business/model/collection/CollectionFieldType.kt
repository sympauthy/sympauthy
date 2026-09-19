package com.sympauthy.business.model.collection

import com.sympauthy.business.model.collection.CollectionOperator.CONTAINS
import com.sympauthy.business.model.collection.CollectionOperator.EQ
import com.sympauthy.business.model.collection.CollectionOperator.GT
import com.sympauthy.business.model.collection.CollectionOperator.GTE
import com.sympauthy.business.model.collection.CollectionOperator.IN
import com.sympauthy.business.model.collection.CollectionOperator.LT
import com.sympauthy.business.model.collection.CollectionOperator.LTE
import com.sympauthy.business.model.collection.CollectionOperator.NE
import com.sympauthy.business.model.collection.CollectionOperator.STARTS_WITH
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * A value a person types, which may hold a comma and is therefore never listed in an `in`.
 */
private val TEXT_OPERATORS = setOf(EQ, NE, CONTAINS, STARTS_WITH)

/**
 * A value out of a set, which is compared for equality and never for order.
 */
private val WORD_OPERATORS = setOf(EQ, NE, IN)

/**
 * A value that has a before and an after.
 */
private val ORDERED_OPERATORS = setOf(EQ, NE, LT, LTE, GT, GTE, IN)

/**
 * What kind of value a field holds, which decides both the operators it admits and how a word a
 * caller sent for it is read.
 *
 * The words with no ordering between them take [WORD_OPERATORS] and the ones a person types take
 * [TEXT_OPERATORS], which is where `in` is withheld: a free-text value may hold a comma, and the
 * comma is what separates the members of an `in` list.
 */
enum class CollectionFieldType(
    /**
     * The operators a field of this type admits unless it narrows the set further, `is_null`
     * excepted — that one is offered by a field a row may carry no value for, whatever its type.
     */
    val operators: Set<CollectionOperator>
) {
    STRING(TEXT_OPERATORS),
    EMAIL(TEXT_OPERATORS),
    PHONE_NUMBER(TEXT_OPERATORS),
    TIMEZONE(WORD_OPERATORS),
    UUID(WORD_OPERATORS),
    ENUM(WORD_OPERATORS),
    BOOLEAN(setOf(EQ, NE)),
    NUMBER(ORDERED_OPERATORS),
    DATE(ORDERED_OPERATORS),
    DATE_TIME(ORDERED_OPERATORS);

    /**
     * [value] as the comparable this type compares and orders by, or null where there is no value or
     * it cannot be read as one.
     *
     * It is the one conversion, and both sides of a comparison go through it: a word a caller wrote
     * in a query string and a value read off a row arrive in whichever form each of them is held in
     * — a date claim is a string, a session's date a `LocalDateTime` — and come back as the same
     * type, so what the comparison answers never turns on which of the two it was.
     */
    fun normalizeOrNull(value: Any?): Comparable<*>? = when (this) {
        BOOLEAN -> when (value) {
            is Boolean -> value
            is String -> value.lowercase().toBooleanStrictOrNull()
            else -> null
        }

        NUMBER -> when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }

        DATE -> when (value) {
            is LocalDate -> value
            is LocalDateTime -> value.toLocalDate()
            is String -> parseOrNull { LocalDate.parse(value) }
            else -> null
        }

        DATE_TIME -> when (value) {
            is LocalDateTime -> value
            is LocalDate -> value.atStartOfDay()
            is String -> parseOrNull { LocalDateTime.parse(value) }
            else -> null
        }

        UUID -> when (value) {
            is java.util.UUID -> value
            is String -> parseOrNull { java.util.UUID.fromString(value) }
            else -> null
        }

        else -> value?.toString()
    }
}

/**
 * The result of [parse], or null where what it was given is not a value of that type.
 *
 * Both parsers this wraps refuse by throwing, and every caller here is asking whether a word is
 * readable rather than insisting that it be read.
 */
private fun <T> parseOrNull(parse: () -> T): T? = try {
    parse()
} catch (_: DateTimeParseException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

package com.sympauthy.business.model.collection

import com.sympauthy.util.wireName

/**
 * One field of a collection: what a caller may filter, order or search on, and the whole of what the
 * collection publishes about it.
 *
 * A collection declares these rather than its query parameters. The parameters a caller writes, the
 * refusals a wrong one earns and the capability document are read off the set, so a collection adding a
 * field gets all three.
 */
data class CollectionField(
    /**
     * The snake_case name the field is sent under, which is what goes in the query string.
     */
    val name: String,
    /**
     * What kind of value the field holds, which decides how a word sent for it is read.
     */
    val type: CollectionFieldType,
    /**
     * The identifier the names of this field are displayed under, with the facet appended — so the
     * field's own name is `$key.name`, and a value's is `$key.<value>.name`.
     */
    val key: String,
    /**
     * The operators the field admits. A field the collection does not filter on declares none.
     */
    val operators: Set<CollectionOperator>,
    /**
     * The values the field holds where the set is closed, and null where it is open: a date, an
     * identifier or a free-text value enumerates nothing, and neither does a boolean, whose type
     * already says what it holds.
     */
    val values: List<CollectionFieldValue>? = null,
    /**
     * Whether the collection orders on this field.
     */
    val sortable: Boolean = false,
    /**
     * Whether `q` matches against this field.
     */
    val searchable: Boolean = false
) {

    /**
     * The value this field holds spelled the way the set spells it, or null where the field is over
     * an open set or holds no such value.
     *
     * Matched ignoring case, as every closed set on this server is, so what a criterion carries is
     * the configured spelling rather than the one that arrived.
     */
    fun valueOrNull(word: String): String? = values
        ?.firstOrNull { it.value.equals(word, ignoreCase = true) }
        ?.value
}

/**
 * One value of a field over a closed set, and the identifier its name is displayed under.
 *
 * The identifier is the value's own rather than the field's where the value is something this
 * deployment configured: a claim is named by `claims.<id>`, so a deployment that named it once has
 * named it here too.
 */
data class CollectionFieldValue(
    val value: String,
    val key: String,
    /**
     * The name to read where no bundle holds [key], which is the value itself unless this deployment
     * already named it — a provider carries a name in the file that declares it, and a filter bar
     * showing the identifier beside a list showing that name would be showing two things.
     */
    val defaultName: String = value
)

/**
 * The values [E] publishes, each named under [key] and the word it is published as.
 */
inline fun <reified E : Enum<E>> enumFieldValues(key: String): List<CollectionFieldValue> =
    enumValues<E>().map { CollectionFieldValue(it.wireName, "$key.${it.wireName}") }

/**
 * The [values] this deployment holds, each named under [key] and its own identifier.
 */
fun fieldValues(key: String, values: Collection<String>): List<CollectionFieldValue> =
    values.map { CollectionFieldValue(it, "$key.$it") }

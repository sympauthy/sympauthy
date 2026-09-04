package com.sympauthy.api.util

import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.util.wireName

/**
 * Resolve the filter query parameter [value] into the criterion it names among the values of [T].
 *
 * A parameter the caller left out is [ValueFilter.Unfiltered], and one naming a value of [T] —
 * compared against the name [publishedName] gives it, ignoring case — is [ValueFilter.Matching].
 * Anything else is [ValueFilter.MatchesNothing]: the caller named something this server does not
 * have, and the listing they get back holds nothing rather than everything.
 *
 * [publishedName] defaults to the [wireName] every enum is published under, and is written out
 * where a value publishes another one.
 */
inline fun <reified T : Enum<T>> valueFilterOf(
    value: String?,
    publishedName: (T) -> String = { it.wireName }
): ValueFilter<T> {
    if (value == null) return ValueFilter.Unfiltered
    return enumValues<T>()
        .firstOrNull { publishedName(it).equals(value, ignoreCase = true) }
        ?.let { ValueFilter.Matching(it) }
        ?: ValueFilter.MatchesNothing
}

package com.sympauthy.util

fun String?.nullIfBlank(): String? = if (!this.isNullOrBlank()) this else null

/**
 * [count] and the word counted, which takes an `s` where the count calls for one.
 *
 * A caller writes [plural] out where that is not the word. An adjective qualifying a noun already named is
 * the same word whatever the count is — "3 generateds" is what the default produces there.
 *
 * One is the only count the singular answers for: none of something is plural in English, so a line
 * reporting nothing reports "0 scopes".
 */
fun pluralize(count: Long, singular: String, plural: String = "${singular}s"): String =
    if (count == 1L) "$count $singular" else "$count $plural"

/**
 * [count] and the word counted, as [pluralize] words a [Long].
 */
fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    pluralize(count.toLong(), singular, plural)

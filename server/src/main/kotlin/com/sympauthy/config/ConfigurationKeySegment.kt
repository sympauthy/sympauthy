package com.sympauthy.config

import io.micronaut.core.naming.NameUtils

/**
 * One segment of a configuration key as an operator wrote it: what they wrote, the name it carries
 * hyphenated, and the index they addressed it by — `[0]`, brackets included, or null.
 *
 * A file written as properties rather than as YAML addresses a list entry by index —
 * `rules.user[0].name`, `allowed-scopes[0]` — and both spell the same key as the YAML the declared keys
 * were named after. The index is kept as text rather than read as a number: nothing here counts with
 * it, and a run of digits longer than an `Int` would be a file taking down the code that exists to
 * report on files.
 */
data class ConfigurationKeySegment(
    val written: String,
    val name: String,
    val index: String?
) {
    val indexed: Boolean get() = index != null

    companion object {

        private const val SEPARATOR = '.'

        private val INDEX = Regex("""\[\d+]$""")

        /** [key] split at its separators, each segment read as the name and the index it carries. */
        fun segmentsOf(key: String): List<ConfigurationKeySegment> = key.split(SEPARATOR).map { segment ->
            val index = INDEX.find(segment)
            ConfigurationKeySegment(
                written = segment,
                name = NameUtils.hyphenate(index?.let { segment.substring(0, it.range.first) } ?: segment),
                index = index?.value
            )
        }
    }
}

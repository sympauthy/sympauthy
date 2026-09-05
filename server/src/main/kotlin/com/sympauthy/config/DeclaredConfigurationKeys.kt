package com.sympauthy.config

import com.sympauthy.config.ConfigurationKeySegment.Companion.segmentsOf
import com.sympauthy.config.DeclaredConfigurationKey.Match

/**
 * Every key the server declares, and what a key an operator wrote comes to against all of them at once.
 *
 * A single key answers whether it covers what was written and what correction it offers; what needs all
 * of them is which prefixes the server answers for at all, whether *anything* binds what was written,
 * and which of the corrections on offer is the nearest. The language the keys are written in is
 * [DeclaredConfigurationKey]'s.
 */
class DeclaredConfigurationKeys(
    private val keys: List<DeclaredConfigurationKey>
) {
    private val roots = keys.mapNotNullTo(mutableSetOf(), DeclaredConfigurationKey::root)

    /**
     * Whether [key] falls under a prefix one of the server's own configuration domains declares — or
     * under one near enough to it to be that prefix misspelt — and is therefore this server's to answer
     * for.
     *
     * A section written under `scope` where the domain is `scopes` is dropped whole and in silence,
     * which is the worst of what this exists to catch, so a prefix that is nearly a declared one is
     * judged as though it were that one. Sharing a word is not enough here as it is further down a key:
     * a deployment writing its own `auth-vars` prefix to interpolate `${auth-vars.issuer}` out of is
     * naming something the server was never going to read, and so is every framework prefix.
     */
    fun answersFor(key: String): Boolean {
        val root = segmentsOf(key).first().name
        return root in roots || roots.any { DeclaredConfigurationKey.isNearly(root, it) }
    }

    /**
     * The keys [key] holds that bind to nothing: [key] itself where no declared key covers it, the
     * entries of the list it names where it is one, and nothing at all where everything under it binds.
     *
     * A list is one key, so `rules.user` arrives holding every rule as its [value] and `rules.user[0].nmae`
     * is invisible to whoever iterates the file. Each entry that is a map is therefore flattened to its
     * own dotted keys and handed back to this same function, which is what makes the entry the operator
     * has to open the thing that gets named.
     */
    fun findUnboundKeys(key: String, value: Any?): List<String> {
        val written = segmentsOf(key)
        val matches = keys.map { it.match(written) }
        return when {
            matches.all { it == Match.UNBOUND } -> listOf(key)
            matches.any { it == Match.OPENS_ENTRIES } -> entriesOf(key, value).flatMap { (entryKey, entryValue) ->
                findUnboundKeys(entryKey, entryValue)
            }

            else -> emptyList()
        }
    }

    /**
     * The declared key nearest to [key], or null where none is near enough to be worth reading.
     *
     * Nearest is the key sharing the longest prefix with [key], and among those the one whose differing
     * segment is nearest. What is offered is the operator's own key with that one segment corrected, and
     * it is offered only once correcting it makes the key bind — so a key with two mistakes in it is
     * named alone rather than half-corrected.
     */
    fun nearestKeyOrNull(key: String): String? {
        val written = segmentsOf(key)
        return keys.mapNotNull { it.correctionOf(written) }
            .filter { binds(it.corrected) }
            .reduceOrNull { nearest, correction -> if (correction.isNearerThan(nearest)) correction else nearest }
            ?.corrected
    }

    private fun binds(key: String): Boolean {
        val written = segmentsOf(key)
        return keys.any { it.match(written) != Match.UNBOUND }
    }

    /**
     * The dotted keys the entries of the list [value] hold, each named as the operator would have to
     * write it to reach that entry. An entry that is not a map holds no key of its own.
     */
    private fun entriesOf(key: String, value: Any?): List<Pair<String, Any?>> {
        val entries = value as? List<*> ?: return emptyList()
        return entries.flatMapIndexed { index, entry ->
            (entry as? Map<*, *>)?.let { flatten("$key[$index]", it) } ?: emptyList()
        }
    }

    private fun flatten(prefix: String, map: Map<*, *>): List<Pair<String, Any?>> = map.entries.flatMap { entry ->
        val key = "$prefix.${entry.key}"
        val value = entry.value
        if (value is Map<*, *> && value.isNotEmpty()) flatten(key, value) else listOf(key to value)
    }
}

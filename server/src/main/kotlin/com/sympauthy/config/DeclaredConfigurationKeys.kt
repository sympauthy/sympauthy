package com.sympauthy.config

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference

/**
 * Every configuration key the server declares, and the rule deciding whether a key an operator wrote
 * binds to one of them.
 *
 * A **pattern** is one declared property's name split on `.`, so `templates.clients.*.allowed-scopes`
 * gives `[templates, clients, *, allowed-scopes]`. There are four kinds of segment: a literal, `*` for
 * an id the operator chooses, `name[*]` for a list they write entries into, and `**` for the subtree a
 * map opens.
 *
 * A key **binds** when it matches, segment for segment, *a prefix of* some pattern. Matching a prefix
 * rather than a whole pattern is one rule doing the work of three: `templates.clients.default.audience`
 * matches a pattern entire, `audiences.default` matches `audiences.*` — which is what the YAML loader
 * flattens an entry with no values under it to — and `templates.clients` matches what `templates:
 * clients: {}` flattens to.
 *
 * The patterns arrive as plain strings, so the rule is held by a test naming its own, with no context
 * to build and nothing to double. [ofTheServer] is where the real ones come from.
 */
class DeclaredConfigurationKeys(
    patterns: List<String>
) {
    private val patterns = patterns.map(::parsePattern)

    private val roots = this.patterns.mapNotNullTo(mutableSetOf()) { (it.firstOrNull() as? Named)?.name }

    /**
     * Whether [key] falls under a prefix one of the server's own configuration domains declares, and is
     * therefore this server's to answer for. A key under any other prefix belongs to a framework that
     * publishes no such list of its own, and is nobody here's to judge.
     */
    fun answersFor(key: String): Boolean = segmentsOf(key).first().name in roots

    /**
     * The keys [key] holds that bind to nothing: [key] itself where it binds to nothing, the entries of
     * the list it names where it is one, and nothing at all where everything under it binds.
     *
     * A list is one key, so `rules.user` arrives holding every rule as its [value] and `rules.user[0].nmae`
     * is invisible to whoever iterates the file. Each entry that is a map is therefore flattened to its
     * own dotted keys and handed back to this same function, which is what makes the entry the operator
     * has to open the thing that gets named.
     */
    fun findUnboundKeys(key: String, value: Any?): List<String> {
        val match = match(segmentsOf(key))
        return when {
            !match.bound -> listOf(key)
            match.opensEntries -> entriesOf(key, value).flatMap { (entryKey, entryValue) ->
                findUnboundKeys(entryKey, entryValue)
            }

            else -> emptyList()
        }
    }

    /**
     * The declared key nearest to [key], or null where none is near enough to be worth reading.
     *
     * Nearest is the key sharing the longest prefix with [key], and among those the one whose differing
     * segment is nearest: a segment sharing a hyphenated word with it comes first — `flow` and
     * `authorization-flow` share one, and no edit distance short enough to reject a coincidence would
     * ever accept that pair — then the smallest edit distance, accepted while it stays within half the
     * shorter of the two segments.
     *
     * The answer is the operator's own key with that one segment corrected, so the ids they chose are
     * the ids they read back, and it is only offered once correcting it makes the key bind.
     */
    fun nearestKeyOrNull(key: String): String? {
        val segments = segmentsOf(key)
        var nearest: Correction? = null
        for (pattern in patterns) {
            val correction = correctionOf(pattern, segments) ?: continue
            if (nearest == null || correction.isNearerThan(nearest)) {
                nearest = correction
            }
        }
        return nearest?.corrected
    }

    /**
     * Where each pattern stands once [segments] have been read, and whether the key stopped where a list
     * begins.
     *
     * An index binds only where a list is: on the `name[*]` a list of entries is declared as, and on the
     * last segment of a key naming a list of values. A section keyed by an id and a section that is one
     * of a kind are neither, so `clients.admin[0].secret` and `advanced.hash[3].block-size` bind to
     * nothing — and so does a list of entries written as a map, which is why `name[*]` demands the index
     * everywhere except at the end of the key, where the list arrives whole as one value.
     */
    private fun match(segments: List<KeySegment>): Match {
        var positions = patterns.map { it to 0 }
        var opensEntries = false
        for ((index, segment) in segments.withIndex()) {
            val last = index == segments.size - 1
            val next = mutableListOf<Pair<List<Segment>, Int>>()
            for ((pattern, position) in positions) {
                val expected = pattern.getOrNull(position) ?: continue
                val named = expected is Named && expected.name == segment.name
                val entries = expected is Entries && expected.name == segment.name
                when {
                    expected is Subtree -> next += pattern to position
                    named && (!segment.indexed || last) -> next += pattern to position + 1
                    expected is Id && !segment.indexed -> next += pattern to position + 1
                    entries && segment.indexed -> next += pattern to position + 1
                    entries && last -> opensEntries = true
                }
            }
            if (next.isEmpty() && !(last && opensEntries)) return Match(bound = false, opensEntries = false)
            positions = next
        }
        return Match(bound = true, opensEntries = opensEntries)
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

    /**
     * The correction [pattern] offers for [segments]: the segment where the two first differ, replaced by
     * the one the pattern declares. Null where the pattern differs nowhere, where the key runs past its
     * end, where the differing segment is nothing like the declared one, or where correcting it leaves a
     * key that still binds to nothing.
     */
    private fun correctionOf(pattern: List<Segment>, segments: List<KeySegment>): Correction? {
        var position = 0
        while (position < segments.size && position < pattern.size) {
            val expected = pattern[position]
            val matches = when (expected) {
                is Subtree -> return null
                is Id -> true
                is Named -> expected.name == segments[position].name
                is Entries -> expected.name == segments[position].name
            }
            if (!matches) break
            position++
        }
        if (position == segments.size || position == pattern.size) return null
        val declared = when (val expected = pattern[position]) {
            is Named -> expected.name
            is Entries -> expected.name
            else -> return null
        }
        val written = segments[position].name
        val sharesWord = declared.split('-').intersect(written.split('-').toSet()).isNotEmpty()
        val distance = editDistance(written, declared)
        if (!sharesWord && distance > minOf(written.length, declared.length) / 2) return null
        val corrected = segments.mapIndexed { index, segment ->
            if (index == position) "$declared${segment.index.orEmpty()}" else segment.written
        }.joinToString(".")
        if (!match(segmentsOf(corrected)).bound) return null
        return Correction(corrected, position, sharesWord, distance)
    }

    private data class Match(
        val bound: Boolean,
        val opensEntries: Boolean
    )

    private data class Correction(
        val corrected: String,
        val prefixLength: Int,
        val sharesWord: Boolean,
        val distance: Int
    ) {
        /**
         * The last comparison is on the answer itself. Without it a tie is broken by the order the
         * patterns were read in, which is the order the classpath happened to hold them, so two
         * operators reading the same file could be told two different things.
         */
        fun isNearerThan(other: Correction): Boolean = when {
            prefixLength != other.prefixLength -> prefixLength > other.prefixLength
            sharesWord != other.sharesWord -> sharesWord
            distance != other.distance -> distance < other.distance
            else -> corrected < other.corrected
        }
    }

    companion object {

        /**
         * The keys the server's own configuration domains declare, read off the bean definitions the
         * annotation processor wrote for them. Those are generated at compile time, so the list is
         * closed by construction rather than by anything remembering to register on it.
         *
         * The definitions are loaded the way the bean context loads them for itself, rather than asked of
         * a context: this answers before one is started, and it answers for a domain a requirement would
         * have disabled, which declares its keys either way.
         */
        fun ofTheServer(): DeclaredConfigurationKeys {
            val patterns = serverBeanDefinitions().flatMap(::patternsOf)
            check(patterns.isNotEmpty()) {
                "No configuration key was found on the classpath, so nothing an operator writes could " +
                    "be judged against one. This is the build being wrong rather than the file."
            }
            return DeclaredConfigurationKeys(patterns)
        }

        /**
         * The bean definitions the server's own classes were compiled into. The definition's own name is
         * what the classpath is filtered on, which is a string the reference already holds — asking for
         * the bean type instead would load every class a framework declares to discard it again.
         *
         * Public alongside [patternsOf] because a test holds the same definitions to a rule of its own.
         */
        fun serverBeanDefinitions(): List<BeanDefinition<*>> {
            val references = mutableListOf<BeanDefinitionReference<*>>()
            SoftServiceLoader.load(BeanDefinitionReference::class.java).collectAll(references)
            return references
                .filter { it.beanDefinitionName.startsWith(SERVER_PACKAGE) && it.isPresent }
                .map { it.load() }
        }

        /**
         * Every key [definition] declares, as a pattern. A map is declared as the subtree it opens,
         * because what an operator writes under it is theirs to name.
         */
        fun patternsOf(definition: BeanDefinition<*>): List<String> {
            if (!definition.annotationMetadata.hasAnnotation(ConfigurationReader::class.java)) return emptyList()
            val declarations =
                definition.injectedMethods.map { it.annotationMetadata to it.arguments.firstOrNull()?.type } +
                    definition.injectedFields.map { it.annotationMetadata to it.asArgument().type } +
                    definition.executableMethods.map { it.annotationMetadata to it.returnType.type }
            return declarations.mapNotNull { (metadata, type) ->
                val name = metadata.stringValue(Property::class.java, "name").orElse(null)
                when {
                    name == null -> null
                    type != null && Map::class.java.isAssignableFrom(type) -> "$name.$SUBTREE"
                    else -> name
                }
            }
        }
    }
}

private const val SERVER_PACKAGE = "com.sympauthy."

private const val SUBTREE = "**"

private const val ENTRIES = "[*]"

private const val ID = "*"

private val INDEX = Regex("""\[\d+]$""")

private sealed interface Segment

private data class Named(val name: String) : Segment

private data object Id : Segment

private data class Entries(val name: String) : Segment

private data object Subtree : Segment

/**
 * One segment of a key an operator wrote: what they wrote, the name it carries hyphenated, and the
 * index as they wrote it — `[0]`, brackets included, or null. A file written as properties rather than
 * as YAML addresses a list entry by index — `rules.user[0].name`, `allowed-scopes[0]` — and both spell
 * the same key as the YAML the patterns were named after.
 *
 * The index is kept as text rather than read as a number: nothing here counts with it, and a run of
 * digits longer than an `Int` would be a file taking down the code that exists to report on files.
 */
private data class KeySegment(
    val written: String,
    val name: String,
    val index: String?
) {
    val indexed: Boolean get() = index != null
}

private fun parsePattern(pattern: String): List<Segment> = pattern.split('.').map { segment ->
    when {
        segment == ID -> Id
        segment == SUBTREE -> Subtree
        segment.endsWith(ENTRIES) -> Entries(NameUtils.hyphenate(segment.removeSuffix(ENTRIES)))
        else -> Named(NameUtils.hyphenate(segment))
    }
}

private fun segmentsOf(key: String): List<KeySegment> = key.split('.').map { segment ->
    val index = INDEX.find(segment)
    KeySegment(
        written = segment,
        name = NameUtils.hyphenate(index?.let { segment.substring(0, it.range.first) } ?: segment),
        index = index?.value
    )
}

private fun editDistance(from: String, to: String): Int {
    var previous = IntArray(to.length + 1) { it }
    for (i in 1..from.length) {
        val current = IntArray(to.length + 1)
        current[0] = i
        for (j in 1..to.length) {
            val substitution = previous[j - 1] + if (from[i - 1] == to[j - 1]) 0 else 1
            current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
        }
        previous = current
    }
    return previous[to.length]
}

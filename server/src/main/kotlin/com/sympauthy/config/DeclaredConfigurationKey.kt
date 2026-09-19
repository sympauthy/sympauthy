package com.sympauthy.config

import io.micronaut.core.naming.NameUtils

/**
 * One key the server declares, as the pattern a key an operator wrote is held against.
 *
 * The pattern is a declared property's name split on `.`, so `templates.clients.*.allowed-scopes` gives
 * `[templates, clients, *, allowed-scopes]`. There are four kinds of segment, and they are the whole of
 * the language:
 *
 * | Segment | Stands for |
 * | --- | --- |
 * | a literal | itself, compared hyphenated |
 * | `*` | an id the operator chooses |
 * | `name[*]` | a list they write entries into |
 * | `**` | the subtree a map opens |
 *
 * The pattern arrives as a string, so a test holds the rule against keys it names itself, with no
 * context to build and nothing to double. [DeclaredConfigurationKeyReader] is where the real ones come
 * from.
 */
class DeclaredConfigurationKey(
    val pattern: String
) {
    private val segments = pattern.split(SEPARATOR).map(::segmentOf)

    /**
     * The prefix this key is declared under, which is the one the domain declaring it is anchored to, or
     * null for a pattern beginning with something other than a literal — which no domain writes.
     */
    val root: String? = (segments.firstOrNull() as? Named)?.name

    /**
     * How [written] stands against this key: bound where it matches, segment for segment, a prefix of
     * this one — which is what makes `audiences.default` bind to `audiences.*.token-audience`, and
     * `templates.clients` to what an empty container flattens to.
     *
     * An index binds only where a list is: on the `name[*]` a list of entries is declared as, and on the
     * last segment of a key naming a list of values. A section keyed by an id and a section that is one
     * of a kind are neither, so `clients.admin[0].secret` and `advanced.hash[3].block-size` bind to
     * nothing — and so does a list of entries written as a map, which is why `name[*]` demands the index
     * everywhere except at the end of the key, where the list arrives whole as one value and the answer
     * is [Match.OPENS_ENTRIES].
     */
    fun match(written: List<ConfigurationKeySegment>): Match {
        var position = 0
        for ((index, segment) in written.withIndex()) {
            val last = index == written.size - 1
            val expected = segments.getOrNull(position) ?: return Match.UNBOUND
            val named = expected is Named && expected.name == segment.name
            val entries = expected is Entries && expected.name == segment.name
            when {
                expected is Subtree -> Unit
                named && (!segment.indexed || last) -> position++
                expected is Id && !segment.indexed -> position++
                entries && segment.indexed -> position++
                entries && last -> return Match.OPENS_ENTRIES
                else -> return Match.UNBOUND
            }
        }
        return Match.BOUND
    }

    /**
     * The correction this key offers for [written]: the segment where the two first differ, replaced by
     * the segments this key declares in its place, and the rest of it left as the operator wrote it —
     * the ids they chose included. Null where the two differ nowhere, where the key runs past this one's
     * end, or where the differing segment is nothing like the ones declared there.
     *
     * How many stand in its place is settled rather than row for. The correction keeps every other
     * segment the operator wrote, so a key one segment longer than theirs replaces one segment with two,
     * and a key no longer than theirs replaces it with one: a name spelt wrong is the second of those,
     * and a name that moved deeper is the first. That is what answers
     * `advanced.authorization-webhook.timeout` with `advanced.webhooks.authorization.timeout`, where the
     * key did not lose a spelling so much as gain a grouping above it.
     *
     * Nothing like them means neither sharing a hyphenated word with them nor standing within
     * [tolerance] of them, the run read as the one hyphenated name its words would spell. The word comes
     * first because `flow` and `authorization-flow` share one, and no edit distance short enough to
     * reject a coincidence would ever accept that pair — nor `authorization-webhook` and the run
     * `webhooks.authorization`, which share their words and nothing of their order.
     */
    fun correctionOf(written: List<ConfigurationKeySegment>): Correction? {
        var position = 0
        while (position < written.size && position < segments.size) {
            val expected = segments[position]
            val matches = when (expected) {
                is Subtree -> return null
                is Id -> true
                is Named -> expected.name == written[position].name
                is Entries -> expected.name == written[position].name
            }
            if (!matches) break
            position++
        }
        if (position == written.size || position == segments.size) return null
        val length = maxOf(1, segments.size - written.size + 1)
        val declaredNames = segments.subList(position, position + length).map { nameOf(it) ?: return null }
        val declared = declaredNames.joinToString(WORD.toString())
        val misspelt = written[position].name
        val sharesWord = declared.split(WORD).intersect(misspelt.split(WORD).toSet()).isNotEmpty()
        val distance = editDistance(misspelt, declared)
        if (!sharesWord && distance > tolerance(misspelt, declared)) return null
        val replacement = declaredNames.mapIndexed { index, name ->
            if (index == declaredNames.lastIndex) "$name${written[position].index.orEmpty()}" else name
        }
        val corrected = written.flatMapIndexed { index, segment ->
            if (index == position) replacement else listOf(segment.written)
        }.joinToString(SEPARATOR.toString())
        return Correction(corrected, position, sharesWord, distance)
    }

    /**
     * The name [segment] would be written out as, or null for the two that stand for what the operator
     * chose: an id is theirs to name, and a subtree is theirs to fill.
     */
    private fun nameOf(segment: Segment): String? = when (segment) {
        is Named -> segment.name
        is Entries -> segment.name
        is Id, is Subtree -> null
    }

    /** How a key an operator wrote stands against a key the server declares. */
    enum class Match {

        /** It matches no prefix of the declared key, so nothing reads what was written under it. */
        UNBOUND,

        /** It matches a prefix of the declared key. */
        BOUND,

        /** It matches, and stops where a list begins, so the entries are in the value it holds. */
        OPENS_ENTRIES
    }

    /**
     * A key an operator wrote with the one segment that differs replaced by what this key declares in
     * its place, and how near the correction is: how much of the key it kept, whether what was written
     * shares a word with what replaced it, and how many edits apart the two stand.
     */
    class Correction(
        val corrected: String,
        private val prefixLength: Int,
        private val sharesWord: Boolean,
        private val distance: Int
    ) {
        /**
         * The last comparison is on the answer itself. Without it a tie is broken by the order the keys
         * were read in, which is the order the classpath happened to hold them, so two operators reading
         * the same file could be told two different things.
         */
        fun isNearerThan(other: Correction): Boolean = when {
            prefixLength != other.prefixLength -> prefixLength > other.prefixLength
            sharesWord != other.sharesWord -> sharesWord
            distance != other.distance -> distance < other.distance
            else -> corrected < other.corrected
        }
    }

    private fun segmentOf(segment: String): Segment = when {
        segment == ID -> Id
        segment == SUBTREE -> Subtree
        segment.endsWith(ENTRIES) -> Entries(NameUtils.hyphenate(segment.removeSuffix(ENTRIES)))
        else -> Named(NameUtils.hyphenate(segment))
    }

    private sealed interface Segment

    private data class Named(val name: String) : Segment

    private data object Id : Segment

    private data class Entries(val name: String) : Segment

    private data object Subtree : Segment

    companion object {

        private const val SEPARATOR = '.'

        private const val ID = "*"

        private const val SUBTREE = "**"

        private const val ENTRIES = "[*]"

        private const val WORD = '-'

        /**
         * The key [name] opens where what it names is a map: everything written under it is the
         * operator's own to name, so the whole subtree binds.
         */
        fun subtreeUnder(name: String) = DeclaredConfigurationKey("$name$SEPARATOR$SUBTREE")

        /**
         * Whether [written] is near enough to [declared] to be read as that name misspelt.
         */
        fun isNearly(written: String, declared: String): Boolean =
            editDistance(written, declared) <= tolerance(written, declared)

        /**
         * How many edits two names may stand apart and still be one of them misspelt: half the shorter,
         * which holds `scope` to `scopes` and keeps everything unlike either apart.
         */
        private fun tolerance(written: String, declared: String): Int =
            minOf(written.length, declared.length) / 2

        /** The number of insertions, deletions and substitutions turning [from] into [to]. */
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
    }
}

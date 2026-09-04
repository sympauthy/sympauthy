package com.sympauthy.business.model.filter

/**
 * What a listing was asked to keep, where the criterion is one value out of a closed set.
 *
 * A criterion has three answers rather than two. A caller naming a value the set does not hold has
 * asked for something this server cannot have, and that is [MatchesNothing] rather than
 * [Unfiltered]: the difference between the two is the difference between a listing answering
 * nothing and one answering everything.
 *
 * [T] is invariant, and [Unfiltered] and [MatchesNothing] are generic rather than singletons of
 * `ValueFilter<Nothing>`, so that the set a criterion is about is fixed at every call: asking a
 * criterion about a value of another type is a compilation error rather than a listing that
 * quietly keeps nothing.
 */
sealed interface ValueFilter<T> {

    /**
     * True if [candidate] passes this criterion.
     */
    fun matches(candidate: T): Boolean

    /**
     * Every value passes: the caller named no criterion.
     */
    class Unfiltered<T> : ValueFilter<T> {
        override fun matches(candidate: T) = true

        // Equality is by shape rather than by identity: two of these are the same criterion, and a
        // criterion is compared as a value wherever one is asserted on.
        override fun equals(other: Any?) = other is Unfiltered<*>
        override fun hashCode() = Unfiltered::class.hashCode()
        override fun toString() = "Unfiltered"
    }

    /**
     * Only [value] passes.
     */
    data class Matching<T>(val value: T) : ValueFilter<T> {
        override fun matches(candidate: T) = value == candidate
    }

    /**
     * Nothing passes: the caller named a value the set does not hold.
     */
    class MatchesNothing<T> : ValueFilter<T> {
        override fun matches(candidate: T) = false

        override fun equals(other: Any?) = other is MatchesNothing<*>
        override fun hashCode() = MatchesNothing::class.hashCode()
        override fun toString() = "MatchesNothing"
    }
}

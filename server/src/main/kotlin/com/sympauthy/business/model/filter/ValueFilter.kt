package com.sympauthy.business.model.filter

/**
 * What a listing was asked to keep, where the criterion is one value out of a closed set.
 *
 * A criterion has three answers rather than two. A caller naming a value the set does not hold has
 * asked for something this server cannot have, and that is [MatchesNothing] rather than
 * [Unfiltered]: the difference between the two is the difference between a listing answering
 * nothing and one answering everything.
 */
sealed interface ValueFilter<out T> {
    /**
     * Every value passes: the caller named no criterion.
     */
    data object Unfiltered : ValueFilter<Nothing>

    /**
     * Only [value] passes.
     */
    data class Matching<T>(val value: T) : ValueFilter<T>

    /**
     * Nothing passes: the caller named a value the set does not hold.
     */
    data object MatchesNothing : ValueFilter<Nothing>
}

/**
 * True if [candidate] passes this criterion.
 */
fun <T> ValueFilter<T>.matches(candidate: T): Boolean = when (this) {
    ValueFilter.Unfiltered -> true
    is ValueFilter.Matching -> value == candidate
    ValueFilter.MatchesNothing -> false
}

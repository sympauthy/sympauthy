package com.sympauthy.business.model.page

/**
 * The page of a collection a caller asked for, once the bounds a deployment sets have accepted it.
 */
data class PageParams(
    val page: Int,
    val size: Int
)

/**
 * One page of a collection, and how many the whole collection holds.
 *
 * A listing answers with this rather than with everything it read, so that the criteria, the order
 * and the slice are one call and can become one query the day the collection outgrows memory.
 */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Int
)

/**
 * Order this list with [comparator], then return the page [params] names out of it.
 *
 * [comparator] must leave no two elements equal: elements it ties are free to swap between two
 * calls, and a caller walking the pages then sees one of them twice and never sees the other.
 */
fun <T> List<T>.orderedPage(
    params: PageParams,
    comparator: Comparator<T>
): Page<T> = Page(
    items = this.sortedWith(comparator)
        .drop(params.page * params.size)
        .take(params.size),
    page = params.page,
    size = params.size,
    total = this.size
)

/**
 * The same page of the same collection, with [transform] applied to every element it holds.
 *
 * Where reading an element costs something, this is what keeps that cost on the page a caller asked
 * for rather than on everything the criteria kept.
 */
inline fun <T, R> Page<T>.map(transform: (T) -> R): Page<R> = Page(
    items = items.map(transform),
    page = page,
    size = size,
    total = total
)

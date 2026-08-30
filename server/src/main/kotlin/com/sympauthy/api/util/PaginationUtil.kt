package com.sympauthy.api.util

const val DEFAULT_PAGE = 0
const val DEFAULT_PAGE_SIZE = 20

data class PageParams(
    val page: Int,
    val size: Int
)

/**
 * Resolve the [page] and [size] query parameters a caller may have omitted, substituting
 * [DEFAULT_PAGE] and [DEFAULT_PAGE_SIZE] for the ones left null.
 */
fun resolvePageParams(
    page: Int?,
    size: Int?
): PageParams = PageParams(
    page = page ?: DEFAULT_PAGE,
    size = size ?: DEFAULT_PAGE_SIZE
)

/**
 * Return the page of this list [params] names, ordered by [comparator].
 *
 * [comparator] must impose a total order — one ending in a key no two elements share. A comparator
 * leaving two elements free to swap decides nothing about which of them a page boundary falls
 * between, so a caller walking the pages can be handed one of them twice and never handed the other.
 */
fun <T> List<T>.orderedPage(
    params: PageParams,
    comparator: Comparator<T>
): List<T> = sortedWith(comparator)
    .drop(params.page * params.size)
    .take(params.size)

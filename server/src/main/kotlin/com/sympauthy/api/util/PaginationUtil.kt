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

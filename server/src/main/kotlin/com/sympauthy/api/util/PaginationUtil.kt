package com.sympauthy.api.util

const val DEFAULT_PAGE = 0
const val DEFAULT_PAGE_SIZE = 20

data class PageParams(
    val page: Int,
    val size: Int
)

/**
 * Resolve nullable pagination query parameters to their default values.
 *
 * @param page Zero-indexed page number, defaults to [DEFAULT_PAGE].
 * @param size Number of results per page, defaults to [DEFAULT_PAGE_SIZE].
 */
fun resolvePageParams(
    page: Int?,
    size: Int?
): PageParams = PageParams(
    page = page ?: DEFAULT_PAGE,
    size = size ?: DEFAULT_PAGE_SIZE
)

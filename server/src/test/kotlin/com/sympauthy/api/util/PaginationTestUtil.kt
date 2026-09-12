package com.sympauthy.api.util

import com.sympauthy.config.model.PaginationConfig
import com.sympauthy.config.model.advancedConfigOf

const val TEST_DEFAULT_PAGE_SIZE = 20
const val TEST_MAX_PAGE_SIZE = 100

/**
 * A [PaginationUtil] bounded by the values a deployment gets when it configures nothing, for the
 * controllers whose tests need paging to work rather than to be configured.
 */
fun defaultPaginationUtil(): PaginationUtil =
    paginationUtilOf(defaultSize = TEST_DEFAULT_PAGE_SIZE, maxSize = TEST_MAX_PAGE_SIZE)

fun paginationUtilOf(
    defaultSize: Int,
    maxSize: Int
): PaginationUtil = PaginationUtil(
    advancedConfigOf(pagination = PaginationConfig(defaultSize = defaultSize, maxSize = maxSize))
)

package com.sympauthy.api.util

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.orThrow
import io.micronaut.http.HttpStatus.BAD_REQUEST
import jakarta.inject.Inject
import jakarta.inject.Singleton

const val DEFAULT_PAGE = 0

/**
 * Resolves the paging query parameters for every paged endpoint, so that all of them answer a caller
 * who sends nothing, and a caller who sends something impossible, the same way.
 *
 * It is a bean rather than a function because the bounds it enforces are a deployment's to set.
 */
@Singleton
class PaginationUtil(
    @Inject private val advancedConfig: AdvancedConfig
) {

    /**
     * Resolve the [page] and [size] a caller sent, substituting the configured defaults for the ones
     * left null, and refuse a pair no collection can be read with.
     *
     * A negative [page] is `pagination.page.negative`, a [size] below one is
     * `pagination.size.too_small`, a [size] above the configured maximum is
     * `pagination.size.too_large`, and a [page] whose offset into the collection does not fit in an
     * `Int` is `pagination.page.too_large`.
     *
     * That last one is not reachable by asking for an absurd size, since the maximum has already
     * refused it — it is reachable by asking for an ordinary size of an enormous page, and the
     * multiplication is what overflows. It is computed in a `Long` for that reason.
     */
    fun resolvePageParams(
        page: Int?,
        size: Int?
    ): PageParams {
        val pagination = advancedConfig.orThrow().pagination
        val resolvedPage = page ?: DEFAULT_PAGE
        val resolvedSize = size ?: pagination.defaultSize

        if (resolvedPage < 0) {
            throw httpExceptionOf(
                BAD_REQUEST, "pagination.page.negative", "description.pagination.page.negative",
                "page" to resolvedPage.toString()
            )
        }
        if (resolvedSize < 1) {
            throw httpExceptionOf(
                BAD_REQUEST, "pagination.size.too_small", "description.pagination.size.too_small",
                "size" to resolvedSize.toString()
            )
        }
        if (resolvedSize > pagination.maxSize) {
            throw httpExceptionOf(
                BAD_REQUEST, "pagination.size.too_large", "description.pagination.size.too_large",
                "size" to resolvedSize.toString(),
                "maxSize" to pagination.maxSize.toString()
            )
        }

        val maxPage = Int.MAX_VALUE.toLong() / resolvedSize
        if (resolvedPage > maxPage) {
            throw httpExceptionOf(
                BAD_REQUEST, "pagination.page.too_large", "description.pagination.page.too_large",
                "page" to resolvedPage.toString(),
                "size" to resolvedSize.toString(),
                "maxPage" to maxPage.toString()
            )
        }

        return PageParams(
            page = resolvedPage,
            size = resolvedSize
        )
    }
}

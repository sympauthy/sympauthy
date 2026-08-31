package com.sympauthy.api.util

import com.sympauthy.api.exception.LocalizedHttpException
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaginationUtilTest {

    private val paginationUtil = paginationUtilOf(defaultSize = 20, maxSize = 100)

    @Test
    fun `resolvePageParams - Substitute the defaults for the parameters left null`() {
        val params = paginationUtil.resolvePageParams(null, null)

        assertEquals(DEFAULT_PAGE, params.page)
        assertEquals(20, params.size)
    }

    @Test
    fun `resolvePageParams - Substitute the configured default size`() {
        val params = paginationUtilOf(defaultSize = 50, maxSize = 100).resolvePageParams(null, null)

        assertEquals(50, params.size)
    }

    @Test
    fun `resolvePageParams - Return the parameters the caller sent`() {
        val params = paginationUtil.resolvePageParams(3, 50)

        assertEquals(3, params.page)
        assertEquals(50, params.size)
    }

    @Test
    fun `resolvePageParams - Accept the first page and the smallest size`() {
        val params = paginationUtil.resolvePageParams(0, 1)

        assertEquals(0, params.page)
        assertEquals(1, params.size)
    }

    @Test
    fun `resolvePageParams - Accept the configured maximum size`() {
        val params = paginationUtil.resolvePageParams(0, 100)

        assertEquals(100, params.size)
    }

    @Test
    fun `resolvePageParams - Reject a negative page`() {
        assertBadRequest("pagination.page.negative") {
            paginationUtil.resolvePageParams(-1, null)
        }
    }

    @Test
    fun `resolvePageParams - Reject a size below one`() {
        assertBadRequest("pagination.size.too_small") {
            paginationUtil.resolvePageParams(null, 0)
        }
    }

    @Test
    fun `resolvePageParams - Reject a negative size`() {
        assertBadRequest("pagination.size.too_small") {
            paginationUtil.resolvePageParams(null, -1)
        }
    }

    @Test
    fun `resolvePageParams - Reject a size above the configured maximum`() {
        assertBadRequest("pagination.size.too_large") {
            paginationUtil.resolvePageParams(null, 101)
        }
    }

    @Test
    fun `resolvePageParams - Reject a page whose offset does not fit in an Int`() {
        assertBadRequest("pagination.page.too_large") {
            paginationUtil.resolvePageParams(200_000_000, 20)
        }
    }

    @Test
    fun `resolvePageParams - Accept the last page whose offset fits in an Int`() {
        val params = paginationUtil.resolvePageParams(Int.MAX_VALUE / 20, 20)

        assertEquals(Int.MAX_VALUE / 20, params.page)
    }

    @Test
    fun `resolvePageParams - Report the page before the offset when both are wrong`() {
        assertBadRequest("pagination.page.negative") {
            paginationUtil.resolvePageParams(-1, 0)
        }
    }

    @Test
    fun `orderedPage - Order the whole list before slicing it`() {
        val page = listOf("d", "b", "a", "c").orderedPage(PageParams(0, 2), naturalOrder())

        assertEquals(listOf("a", "b"), page)
    }

    @Test
    fun `orderedPage - Return the page the parameters name`() {
        val page = listOf("d", "b", "a", "c").orderedPage(PageParams(1, 2), naturalOrder())

        assertEquals(listOf("c", "d"), page)
    }

    @Test
    fun `orderedPage - Return the elements a last page is short of a full one`() {
        val page = listOf("c", "a", "b").orderedPage(PageParams(1, 2), naturalOrder())

        assertEquals(listOf("c"), page)
    }

    @Test
    fun `orderedPage - Return nothing for a page past the end`() {
        val page = listOf("c", "a", "b").orderedPage(PageParams(5, 2), naturalOrder())

        assertEquals(emptyList<String>(), page)
    }

    private fun assertBadRequest(
        detailsId: String,
        executable: () -> Unit
    ) {
        val exception = assertThrows<LocalizedHttpException> { executable() }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(detailsId, exception.detailsId)
    }
}

package com.sympauthy.api.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaginationUtilTest {

    private data class Row(val key: String, val id: Int)

    private val comparator = compareBy<Row>({ it.key }, { it.id })

    @Test
    fun `resolvePageParams - Substitute the defaults for the omitted parameters`() {
        assertEquals(PageParams(DEFAULT_PAGE, DEFAULT_PAGE_SIZE), resolvePageParams(null, null))
    }

    @Test
    fun `resolvePageParams - Keep the parameters the caller sent`() {
        assertEquals(PageParams(3, 5), resolvePageParams(3, 5))
    }

    @Test
    fun `orderedPage - Order before slicing`() {
        val rows = listOf(Row("c", 1), Row("a", 2), Row("b", 3))

        val page = rows.orderedPage(PageParams(page = 0, size = 2), comparator)

        assertEquals(listOf(Row("a", 2), Row("b", 3)), page)
    }

    @Test
    fun `orderedPage - Break a tie on the second key`() {
        val rows = listOf(Row("a", 3), Row("a", 1), Row("a", 2))

        val page = rows.orderedPage(PageParams(page = 0, size = 3), comparator)

        assertEquals(listOf(Row("a", 1), Row("a", 2), Row("a", 3)), page)
    }

    @Test
    fun `orderedPage - Return the elements the page covers`() {
        val rows = (1..7).map { Row("a", it) }

        val page = rows.orderedPage(PageParams(page = 1, size = 3), comparator)

        assertEquals(listOf(Row("a", 4), Row("a", 5), Row("a", 6)), page)
    }

    @Test
    fun `orderedPage - Return nothing past the last page`() {
        val rows = (1..3).map { Row("a", it) }

        val page = rows.orderedPage(PageParams(page = 2, size = 3), comparator)

        assertEquals(emptyList<Row>(), page)
    }
}

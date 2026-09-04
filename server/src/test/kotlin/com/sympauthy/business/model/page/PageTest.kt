package com.sympauthy.business.model.page

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageTest {

    @Test
    fun `orderedPage - Order the whole list before slicing it`() {
        val page = listOf("d", "b", "a", "c").orderedPage(PageParams(0, 2), naturalOrder())

        assertEquals(listOf("a", "b"), page.items)
    }

    @Test
    fun `orderedPage - Return the page the parameters name`() {
        val page = listOf("d", "b", "a", "c").orderedPage(PageParams(1, 2), naturalOrder())

        assertEquals(listOf("c", "d"), page.items)
        assertEquals(1, page.page)
        assertEquals(2, page.size)
    }

    @Test
    fun `orderedPage - Return the elements a last page is short of a full one`() {
        val page = listOf("c", "a", "b").orderedPage(PageParams(1, 2), naturalOrder())

        assertEquals(listOf("c"), page.items)
    }

    @Test
    fun `orderedPage - Return nothing for a page past the end`() {
        val page = listOf("c", "a", "b").orderedPage(PageParams(5, 2), naturalOrder())

        assertEquals(emptyList<String>(), page.items)
    }

    @Test
    fun `orderedPage - Count the whole list the page was read out of`() {
        val page = listOf("c", "a", "b").orderedPage(PageParams(0, 2), naturalOrder())

        assertEquals(3, page.total)
    }

    @Test
    fun `map - Transform the elements of the page, and nothing else`() {
        val page = listOf("c", "a", "b").orderedPage(PageParams(0, 2), naturalOrder()).map(String::uppercase)

        assertEquals(listOf("A", "B"), page.items)
        assertEquals(0, page.page)
        assertEquals(2, page.size)
        assertEquals(3, page.total)
    }
}

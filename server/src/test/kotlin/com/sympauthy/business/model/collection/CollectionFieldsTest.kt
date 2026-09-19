package com.sympauthy.business.model.collection

import com.sympauthy.api.util.criteriaOf
import com.sympauthy.business.model.collection.CollectionFieldType.BOOLEAN
import com.sympauthy.business.model.collection.CollectionFieldType.DATE
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.NUMBER
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.page.PageParams
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime

class CollectionFieldsTest {

    private data class Row(
        val id: String,
        val name: String? = null,
        val weight: Long = 0,
        val enabled: Boolean = true,
        val startedAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
        /** Read as a string, the way a date claim is stored. */
        val birthDate: String? = null,
        val tags: List<String> = emptyList()
    )

    private val firstPage = PageParams(page = 0, size = 20)

    private suspend fun fields() = collectionFields<Row>(uniqueKey = compareBy(Row::id)) {
        field("id", STRING, read = Row::id)
        field("name", STRING, nullable = true, sortable = true, searchable = true, read = Row::name)
        field("weight", NUMBER, sortable = true, read = Row::weight)
        field("enabled", BOOLEAN, sortable = true, read = Row::enabled)
        field("started_at", DATE_TIME, sortable = true, read = Row::startedAt)
        field("birth_date", DATE, nullable = true, sortable = true, read = Row::birthDate)
        field("tag", STRING, nullable = true, searchable = true, read = Row::tags)
        defaultSort("-weight")
    }

    private suspend fun page(
        vararg filters: Pair<String, String>,
        sort: String? = null,
        query: String? = null,
        pageParams: PageParams = firstPage,
        rows: List<Row>
    ): List<String> {
        val fields = fields()
        val criteria = fields.capabilities.criteriaOf(*filters, sort = sort, query = query)
        return fields.page(rows, criteria, pageParams).items.map(Row::id)
    }

    @Test
    fun `page - Keep the rows an exact match names`() = runTest {
        val rows = listOf(Row("a", name = "Ariane"), Row("b", name = "Bruno"))

        assertEquals(listOf("a"), page("name" to "Ariane", rows = rows))
    }

    @Test
    fun `page - Keep the rows an exact match does not name under ne`() = runTest {
        val rows = listOf(Row("a", name = "Ariane"), Row("b", name = "Bruno"))

        assertEquals(listOf("b"), page("name.ne" to "Ariane", rows = rows))
    }

    @Test
    fun `page - Keep a row carrying no value at all under ne`() = runTest {
        val rows = listOf(Row("a", name = "Ariane"), Row("b"))

        assertEquals(listOf("b"), page("name.ne" to "Ariane", rows = rows))
    }

    @Test
    fun `page - Match a fragment of a value, ignoring case, under contains`() = runTest {
        val rows = listOf(Row("a", name = "Ariane"), Row("b", name = "Bruno"))

        assertEquals(listOf("a"), page("name.contains" to "RIA", rows = rows))
    }

    @Test
    fun `page - Match the beginning of a value, ignoring case, under starts_with`() = runTest {
        val rows = listOf(Row("a", name = "Ariane"), Row("b", name = "Bruno"))

        assertEquals(listOf("b"), page("name.starts_with" to "bru", rows = rows))
    }

    @Test
    fun `page - Keep the rows one of a comma-separated list names under in`() = runTest {
        val rows = listOf(Row("a", weight = 1), Row("b", weight = 2), Row("c", weight = 3))

        assertEquals(listOf("a", "c"), page("weight.in" to "1,3", sort = "weight", rows = rows))
    }

    @Test
    fun `page - Compare a number rather than the text it is written as`() = runTest {
        val rows = listOf(Row("a", weight = 9), Row("b", weight = 10))

        assertEquals(listOf("b"), page("weight.gt" to "9", rows = rows))
    }

    @Test
    fun `page - Compare a date read off a value the row stores as text`() = runTest {
        val rows = listOf(Row("a", birthDate = "1990-01-02"), Row("b", birthDate = "2001-12-31"))

        assertEquals(listOf("b"), page("birth_date.gte" to "2000-01-01", rows = rows))
    }

    @Test
    fun `page - Keep the rows carrying no value under is_null`() = runTest {
        val rows = listOf(Row("a", name = "Ariane"), Row("b"))

        assertEquals(listOf("b"), page("name.is_null" to "true", rows = rows))
    }

    @Test
    fun `page - Keep the rows carrying one under is_null false`() = runTest {
        val rows = listOf(Row("a", name = "Ariane"), Row("b"))

        assertEquals(listOf("a"), page("name.is_null" to "false", rows = rows))
    }

    @Test
    fun `page - Compose two criteria over one field with and`() = runTest {
        val rows = listOf(Row("a", weight = 1), Row("b", weight = 5), Row("c", weight = 9))

        assertEquals(listOf("b"), page("weight.gte" to "2", "weight.lte" to "8", rows = rows))
    }

    @Test
    fun `page - Keep a row where any of the values a field reads satisfies the criterion`() = runTest {
        val rows = listOf(Row("a", tags = listOf("red", "blue")), Row("b", tags = listOf("green")))

        assertEquals(listOf("a"), page("tag" to "blue", rows = rows))
    }

    @Test
    fun `page - Ask ne of the row rather than of a value where a field reads several`() = runTest {
        val rows = listOf(Row("a", tags = listOf("red", "blue")), Row("b", tags = listOf("green")))

        // "a" holds a tag that is not red, and is dropped all the same: ne asks that the row hold no
        // red tag, which is what a caller excluding red asked for.
        assertEquals(listOf("b"), page("tag.ne" to "red", rows = rows))
    }

    @Test
    fun `page - Keep the rows a free text search matches against any searchable field`() = runTest {
        val rows = listOf(Row("a", name = "Ariane"), Row("b", tags = listOf("ariadne")))

        assertEquals(listOf("a", "b"), page(query = "aria", rows = rows))
    }

    @Test
    fun `page - Narrow what the filters kept with the free text search rather than widening it`() = runTest {
        val rows = listOf(Row("a", name = "Ariane", weight = 1), Row("b", name = "Ariane", weight = 2))

        assertEquals(listOf("a"), page("weight" to "1", query = "aria", rows = rows))
    }

    @Test
    fun `page - Take the collection's own order where the caller names no key`() = runTest {
        val rows = listOf(Row("a", weight = 1), Row("b", weight = 3), Row("c", weight = 2))

        assertEquals(listOf("b", "c", "a"), page(rows = rows))
    }

    @Test
    fun `page - Read the keys left to right`() = runTest {
        val rows = listOf(
            Row("a", enabled = false, weight = 9),
            Row("b", enabled = true, weight = 1),
            Row("c", enabled = true, weight = 2)
        )

        assertEquals(listOf("c", "b", "a"), page(sort = "-enabled,-weight", rows = rows))
    }

    @Test
    fun `page - End the order on the unique key, ascending, whichever direction is asked for`() = runTest {
        val rows = listOf(Row("b", weight = 1), Row("a", weight = 1))

        assertEquals(listOf("a", "b"), page(sort = "-weight", rows = rows))
    }

    @Test
    fun `page - Order a row carrying no value for the key last`() = runTest {
        val rows = listOf(Row("a"), Row("b", name = "Bruno"))

        assertEquals(listOf("b", "a"), page(sort = "name", rows = rows))
    }

    @Test
    fun `page - Answer the slice the parameters name, out of everything the criteria kept`() = runTest {
        val rows = listOf(Row("a", weight = 3), Row("b", weight = 2), Row("c", weight = 1))
        val fields = fields()
        val criteria = fields.capabilities.criteriaOf()

        val result = fields.page(rows, criteria, PageParams(page = 1, size = 2))

        assertEquals(listOf("c"), result.items.map(Row::id))
        assertEquals(3, result.total)
    }

    @Test
    fun `collectionFields - Offer is_null only on a field a row may carry no value for`() = runTest {
        val fields = fields().capabilities

        assertTrue(CollectionOperator.IS_NULL in fields.fieldOrNull("name")!!.operators)
        assertTrue(CollectionOperator.IS_NULL !in fields.fieldOrNull("weight")!!.operators)
    }

    @Test
    fun `collectionFields - Withhold in from a field whose values may hold a comma`() = runTest {
        val fields = fields().capabilities

        assertTrue(CollectionOperator.IN !in fields.fieldOrNull("name")!!.operators)
        assertTrue(CollectionOperator.IN in fields.fieldOrNull("weight")!!.operators)
    }

    @Test
    fun `collectionFields - Refuse a field name the collection already declared`() = runTest {
        val failure = assertThrows<IllegalArgumentException> {
            collectionFields<Row>(uniqueKey = compareBy(Row::id)) {
                field("name", STRING, read = Row::name)
                field("name", STRING, read = Row::id)
            }
        }

        assertEquals("This collection declares the field name twice.", failure.message)
    }

    @Test
    fun `collectionFields - Publish the collection's own order the way sort is spelled`() = runTest {
        assertEquals(listOf("-weight"), fields().capabilities.defaultSort.map(CollectionSortKey::spelling))
    }

    @Test
    fun `normalizeOrNull - Read a date off the value and off the text it is written as`() {
        val date = LocalDate.of(1990, 1, 2)

        assertEquals(date, DATE.normalizeOrNull(date))
        assertEquals(date, DATE.normalizeOrNull("1990-01-02"))
    }

    @Test
    fun `normalizeOrNull - Answer nothing for a value the type cannot read`() {
        assertEquals(null, DATE.normalizeOrNull("the second of January"))
        assertEquals(null, NUMBER.normalizeOrNull("a lot"))
        assertEquals(null, BOOLEAN.normalizeOrNull("yes"))
    }
}

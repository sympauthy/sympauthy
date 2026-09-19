package com.sympauthy.api.util

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.CollectionSortKey
import io.micronaut.http.HttpStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class CollectionCriteriaUtilTest {

    private val name = CollectionField(
        name = "name",
        type = STRING,
        key = "fields.name",
        operators = setOf(CollectionOperator.EQ, CollectionOperator.CONTAINS),
        sortable = true,
        searchable = true
    )

    private val status = CollectionField(
        name = "status",
        type = ENUM,
        key = "fields.status",
        operators = setOf(CollectionOperator.EQ, CollectionOperator.IN),
        values = listOf(
            CollectionFieldValue("enabled", "fields.status.enabled"),
            CollectionFieldValue("disabled", "fields.status.disabled")
        )
    )

    private val createdAt = CollectionField(
        name = "created_at",
        type = DATE_TIME,
        key = "fields.created_at",
        operators = setOf(CollectionOperator.GTE),
        sortable = true
    )

    /** A field the collection orders on and does not filter on. */
    private val rank = CollectionField(
        name = "rank",
        type = STRING,
        key = "fields.rank",
        operators = emptySet(),
        sortable = true
    )

    private val capabilities = CollectionCapabilities(
        fields = listOf(name, status, createdAt, rank),
        defaultSort = listOf(CollectionSortKey(createdAt, descending = false))
    )

    private val searchesNothing = CollectionCapabilities(
        fields = listOf(status),
        defaultSort = emptyList()
    )

    private fun criteriaOf(
        query: String = "",
        sort: String? = null,
        q: String? = null,
        capabilities: CollectionCapabilities = this.capabilities,
        reserved: Set<String> = emptySet()
    ) = collectionCriteriaOf(collectionRequest(query), capabilities, sort, q, reserved)

    @Test
    fun `collectionCriteriaOf - Read a bare parameter as an exact match`() {
        val criterion = criteriaOf("name=Ariane").filters.single()

        assertEquals(name, criterion.field)
        assertEquals(CollectionOperator.EQ, criterion.operator)
        assertEquals(listOf("Ariane"), criterion.values)
    }

    @Test
    fun `collectionCriteriaOf - Read the operator a dotted suffix names`() {
        val criterion = criteriaOf("name.contains=ria").filters.single()

        assertEquals(CollectionOperator.CONTAINS, criterion.operator)
        assertEquals(listOf("ria"), criterion.values)
    }

    @Test
    fun `collectionCriteriaOf - Read a value as the type of the field it is sent for`() {
        val criterion = criteriaOf("created_at.gte=2026-01-01T00:00:00").filters.single()

        assertEquals(listOf(LocalDateTime.of(2026, 1, 1, 0, 0)), criterion.values)
    }

    @Test
    fun `collectionCriteriaOf - Answer a closed set value as the set spells it`() {
        val criterion = criteriaOf("status=ENABLED").filters.single()

        assertEquals(listOf("enabled"), criterion.values)
    }

    @Test
    fun `collectionCriteriaOf - Split a comma-separated list under in`() {
        val criterion = criteriaOf("status.in=enabled,disabled").filters.single()

        assertEquals(listOf("enabled", "disabled"), criterion.values)
    }

    @Test
    fun `collectionCriteriaOf - Compose two parameters naming one field`() {
        val criteria = criteriaOf("created_at.gte=2026-01-01T00:00:00&name=Ariane")

        assertEquals(setOf("created_at", "name"), criteria.filters.map { it.field.name }.toSet())
    }

    @Test
    fun `collectionCriteriaOf - Leave the paging parameters out of the criteria`() {
        assertEquals(emptyList<Any>(), criteriaOf("page=1&size=2&sort=name&q=aria").filters)
    }

    @Test
    fun `collectionCriteriaOf - Leave a parameter the collection reads for something else out of them`() {
        assertEquals(emptyList<Any>(), criteriaOf("claims=email", reserved = setOf("claims")).filters)
    }

    @Test
    fun `collectionCriteriaOf - Take the collection's own order where no key is named`() {
        assertEquals(capabilities.defaultSort, criteriaOf().sort)
    }

    @Test
    fun `collectionCriteriaOf - Read the keys the caller named, left to right`() {
        val sort = criteriaOf(sort = "-created_at,name").sort

        assertEquals(listOf("-created_at", "name"), sort.map(CollectionSortKey::spelling))
    }

    @Test
    fun `collectionCriteriaOf - Answer no free text where the caller sent none`() {
        assertNull(criteriaOf().query)
    }

    @Test
    fun `collectionCriteriaOf - Refuse a parameter naming no field this collection filters on`() {
        val exception = assertThrows<LocalizedHttpException> { criteriaOf("surname=Ariane") }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.unknown_field", exception.detailsId)
        assertEquals("surname", exception.values["parameter"])
    }

    @Test
    fun `collectionCriteriaOf - Refuse a parameter naming a field it only orders on`() {
        val exception = assertThrows<LocalizedHttpException> { criteriaOf("rank=first") }

        assertEquals("collection.filter.unknown_field", exception.detailsId)
    }

    @Test
    fun `collectionCriteriaOf - Refuse an operator the field does not admit`() {
        val exception = assertThrows<LocalizedHttpException> { criteriaOf("status.contains=ena") }

        assertEquals("collection.filter.unsupported_operator", exception.detailsId)
        assertEquals("contains", exception.values["operator"])
    }

    @Test
    fun `collectionCriteriaOf - Refuse a suffix naming no operator at all`() {
        val exception = assertThrows<LocalizedHttpException> { criteriaOf("name.equals=Ariane") }

        assertEquals("collection.filter.unsupported_operator", exception.detailsId)
    }

    @Test
    fun `collectionCriteriaOf - Refuse a value the field's set does not hold`() {
        val exception = assertThrows<LocalizedHttpException> { criteriaOf("status=paused") }

        assertEquals("collection.filter.value.unsupported", exception.detailsId)
        assertEquals("enabled, disabled", exception.values["supportedValues"])
    }

    @Test
    fun `collectionCriteriaOf - Refuse a value the field's type cannot read`() {
        val exception = assertThrows<LocalizedHttpException> { criteriaOf("created_at.gte=yesterday") }

        assertEquals("collection.filter.value.malformed", exception.detailsId)
        assertEquals("date_time", exception.values["type"])
    }

    @Test
    fun `collectionCriteriaOf - Refuse a sort key this collection is not ordered by`() {
        val exception = assertThrows<LocalizedHttpException> { criteriaOf(sort = "surname") }

        assertEquals("collection.sort.unknown_field", exception.detailsId)
        assertEquals("surname", exception.values["field"])
    }

    @Test
    fun `collectionCriteriaOf - Refuse a key a collection binding no sort parameter was sent`() {
        val exception = assertThrows<LocalizedHttpException> {
            criteriaOf("sort=surname", capabilities = searchesNothing)
        }

        assertEquals("collection.sort.unknown_field", exception.detailsId)
    }

    @Test
    fun `collectionCriteriaOf - Refuse a free text search this collection cannot answer`() {
        val exception = assertThrows<LocalizedHttpException> {
            criteriaOf("q=aria", capabilities = searchesNothing)
        }

        assertEquals("collection.search.unsupported", exception.detailsId)
        assertEquals("q", exception.values["parameter"])
    }
}

package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFieldValue
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.CollectionSortKey
import com.sympauthy.util.DEFAULT_LOCALE
import io.micronaut.context.i18n.ResourceBundleMessageSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Reads the bundles the server ships rather than doubles of them, because what the mapper answers
 * for a key with no entry is the rule under test: a field named nowhere falls back to the name it is
 * sent under, and one this deployment already named is read under the key that names it.
 */
class AdminCollectionCapabilitiesResourceMapperTest {

    private val mapper = AdminCollectionCapabilitiesResourceMapper(
        adminMessageSource = ResourceBundleMessageSource("admin_messages", DEFAULT_LOCALE),
        displayMessageSource = ResourceBundleMessageSource("display_messages", DEFAULT_LOCALE)
    )

    private val status = CollectionField(
        name = "status",
        type = ENUM,
        key = "fields.user_status",
        operators = setOf(CollectionOperator.EQ, CollectionOperator.IN),
        values = listOf(
            CollectionFieldValue("enabled", "fields.user_status.enabled"),
            CollectionFieldValue("disabled", "fields.user_status.disabled")
        )
    )

    private val email = CollectionField(
        name = "email",
        type = STRING,
        key = "claims.email",
        operators = setOf(CollectionOperator.CONTAINS),
        sortable = true,
        searchable = true
    )

    private val createdAt = CollectionField(
        name = "created_at",
        type = DATE_TIME,
        key = "fields.created_at",
        operators = setOf(CollectionOperator.GTE),
        sortable = true
    )

    private fun toResource(
        fields: List<CollectionField>,
        defaultSort: List<CollectionSortKey> = emptyList()
    ) = mapper.toResource(CollectionCapabilities(fields, defaultSort), DEFAULT_LOCALE)

    @Test
    fun `toResource - Describe a field by what it is sent under, read under, holds and admits`() {
        val filter = toResource(listOf(status)).filters.single()

        assertEquals("status", filter.field)
        assertEquals("Status", filter.name)
        assertEquals("enum", filter.type)
        assertEquals(listOf("eq", "in"), filter.operators)
    }

    @Test
    fun `toResource - Enumerate the values of a field over a closed set, each with its own name`() {
        val values = toResource(listOf(status)).filters.single().values

        assertEquals(listOf("enabled" to "Enabled", "disabled" to "Disabled"), values?.map { it.value to it.name })
    }

    @Test
    fun `toResource - Enumerate nothing for a field over an open set`() {
        assertNull(toResource(listOf(email)).filters.single().values)
    }

    @Test
    fun `toResource - Read a field that is a configured item under the key naming that item`() {
        assertEquals("Email", toResource(listOf(email)).filters.single().name)
    }

    @Test
    fun `toResource - Fall back to the name a field is sent under where no bundle holds its key`() {
        val unnamed = CollectionField(
            name = "favourite_colour",
            type = STRING,
            key = "claims.favourite_colour",
            operators = setOf(CollectionOperator.EQ)
        )

        assertEquals("favourite_colour", toResource(listOf(unnamed)).filters.single().name)
    }

    @Test
    fun `toResource - Name the fields a free text search matches against`() {
        assertEquals(listOf("email"), toResource(listOf(status, email)).search?.fields)
    }

    @Test
    fun `toResource - Answer no search where the collection names no searchable field`() {
        assertNull(toResource(listOf(status)).search)
    }

    @Test
    fun `toResource - Name the fields the collection orders on and no other`() {
        assertEquals(listOf("email", "created_at"), toResource(listOf(status, email, createdAt)).sorts.map { it.field })
    }

    @Test
    fun `toResource - Spell the collection's own order the way sort is spelled`() {
        val defaultSort = listOf(CollectionSortKey(createdAt, descending = true), CollectionSortKey(email, false))

        assertEquals("-created_at,email", toResource(listOf(email, createdAt), defaultSort).defaultSort)
    }

    @Test
    fun `toResource - Answer no order where the collection's own is the unique key it ends on`() {
        assertNull(toResource(listOf(createdAt)).defaultSort)
    }
}

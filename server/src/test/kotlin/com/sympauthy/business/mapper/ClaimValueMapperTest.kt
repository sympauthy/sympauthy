package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.claim.ClaimDataType.BOOLEAN
import com.sympauthy.business.model.user.claim.ClaimDataType.DATE
import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ClaimValueMapperTest {

    private val mapper = ClaimValueMapper(ObjectMapper.getDefault())

    @Test
    fun `toBusiness - Return null for a claim holding no value`() {
        assertNull(mapper.toBusiness(null, STRING))
    }

    @Test
    fun `toBusiness - Read a boolean claim as a Boolean`() {
        assertEquals(true, mapper.toBusiness("true", BOOLEAN))
        assertEquals(false, mapper.toBusiness("false", BOOLEAN))
    }

    @Test
    fun `toBusiness - Read a boolean claim written as a string as a Boolean`() {
        // A boolean claim held its value as the string "true" until it became a Boolean, and which claim a
        // row belongs to is configuration rather than a column, so those rows are read rather than migrated.
        assertEquals(true, mapper.toBusiness("\"true\"", BOOLEAN))
        assertEquals(false, mapper.toBusiness("\"false\"", BOOLEAN))
    }

    @Test
    fun `toBusiness - Return null where a boolean claim holds something that is not one`() {
        // The object mapper reads every one of these as a truth value if it is asked to convert rather
        // than to read, and a claim retyped to boolean would then publish a false nothing ever stored.
        assertNull(mapper.toBusiness("\"maybe\"", BOOLEAN))
        assertNull(mapper.toBusiness("\"TRUE\"", BOOLEAN))
        assertNull(mapper.toBusiness("\"\"", BOOLEAN))
        assertNull(mapper.toBusiness("0", BOOLEAN))
        assertNull(mapper.toBusiness("42", BOOLEAN))
        assertNull(mapper.toBusiness("[1]", BOOLEAN))
    }

    @Test
    fun `toBusiness - Read a number claim as a Long`() {
        assertEquals(42L, mapper.toBusiness("42", NUMBER))
    }

    @Test
    fun `toBusiness - Read a string claim as a String`() {
        assertEquals("2024-01-15", mapper.toBusiness("\"2024-01-15\"", DATE))
    }

    @Test
    fun `toBusiness - Return null where the value does not read as the claim's type`() {
        assertNull(mapper.toBusiness("\"nineteen\"", NUMBER))
    }

    @Test
    fun `toEntity - Write a boolean as a JSON boolean`() {
        assertEquals("true", mapper.toEntity(true))
    }

    @Test
    fun `toEntity - Write a number as a JSON number`() {
        assertEquals("42", mapper.toEntity(42L))
    }
}

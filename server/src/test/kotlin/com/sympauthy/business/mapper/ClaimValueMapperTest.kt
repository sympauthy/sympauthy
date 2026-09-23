package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.claim.ClaimDataType.BOOLEAN
import com.sympauthy.business.model.user.claim.ClaimDataType.DATE
import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The spellings a claim value reaches a column in, and the key a row is found by.
 *
 * The hash is pinned to the values it answers today: a row is looked up by it and by nothing else, so
 * two versions disagreeing about it makes every account unreachable rather than merely slow.
 */
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

    @Test
    fun `toFoldedValue - Folds the case and drops the quoting the stored value carries`() {
        assertEquals("ada@example.com", mapper.toFoldedValue("Ada@Example.COM"))
        assertNull(mapper.toFoldedValue(null))
    }

    @Test
    fun `toFoldedValue - Answers one value for a number and the text of that number`() {
        // `42` under an employee-number claim and `42` under a username one are one identity; the encoding
        // `value` carries would make them two.
        assertEquals(mapper.toFoldedValue(42L), mapper.toFoldedValue("42"))
    }

    @Test
    fun `toFoldedValueOfStored - Recovers the fold from what the row publishes`() {
        val stored = mapper.toEntity("Ada.Lovelace")

        assertEquals("ada.lovelace", mapper.toFoldedValueOfStored(stored, STRING))
        assertEquals("42", mapper.toFoldedValueOfStored(mapper.toEntity(42L), NUMBER))
        assertNull(mapper.toFoldedValueOfStored(null, STRING))
    }

    @Test
    fun `toFoldedEqualityHash - Maps a value to the key it has always mapped to`() {
        assertEquals(8_235_253_338_125_334_224, mapper.toFoldedEqualityHash("someone@example.com"))
        assertEquals(8_235_253_338_125_334_224, mapper.toFoldedEqualityHash("SOMEONE@Example.com"))
        assertEquals(-2_039_914_840_885_289_964, mapper.toFoldedEqualityHash(""))
        assertNull(mapper.toFoldedEqualityHash(null))
    }

    @Test
    fun `toFoldedEqualityHash - Answers a different key for a different value`() {
        assertNotEquals(
            mapper.toFoldedEqualityHash("someone@example.com"),
            mapper.toFoldedEqualityHash("someone.else@example.com")
        )
    }
}

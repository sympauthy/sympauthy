package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.claim.ClaimDataType
import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The two spellings a claim value reaches a column in, and the hash a row is found by.
 *
 * The hash is pinned to the values it answers today: a row is looked up by it and by nothing else, so
 * two versions disagreeing about it makes every account unreachable rather than merely slow.
 */
class ClaimValueMapperTest {

    private val mapper = ClaimValueMapper(ObjectMapper.getDefault())

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

        assertEquals("ada.lovelace", mapper.toFoldedValueOfStored(stored, ClaimDataType.STRING))
        assertEquals("42", mapper.toFoldedValueOfStored(mapper.toEntity(42L), ClaimDataType.NUMBER))
        assertNull(mapper.toFoldedValueOfStored(null, ClaimDataType.STRING))
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

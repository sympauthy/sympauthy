package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.util.assertThrowsLocalizedException
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType.*
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ClaimValueValidatorTest {

    @InjectMockKs
    lateinit var validator: ClaimValueValidator

    private fun mockClaim(
        dataType: com.sympauthy.business.model.user.claim.ClaimDataType = STRING,
        allowedValues: List<Any>? = null
    ): Claim = mockk {
        every { this@mockk.id } returns "test_claim"
        every { this@mockk.dataType } returns dataType
        every { this@mockk.allowedValues } returns allowedValues
    }

    // --- validateAndCleanValueForClaim ---

    @Test
    fun `validateAndCleanValueForClaim - Returns empty Optional for null value`() {
        val claim = mockClaim()
        val result = validator.validateAndCleanValueForClaim(claim, null)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `validateAndCleanValueForClaim - Throws if value type does not match claim dataType`() {
        val claim = mockClaim(dataType = STRING)
        assertThrowsLocalizedException("user.claim_value_validator.invalid_type") {
            validator.validateAndCleanValueForClaim(claim, 123)
        }
    }

    @Test
    fun `validateAndCleanValueForClaim - Throws if value not in allowedValues`() {
        val claim = mockClaim(allowedValues = emptyList())
        assertThrowsLocalizedException("user.claim_value_validator.invalid_value") {
            validator.validateAndCleanValueForClaim(claim, "value")
        }
    }

    @Test
    fun `validateAndCleanValueForClaim - Accepts value present in allowedValues`() {
        val claim = mockClaim(allowedValues = listOf("allowed"))
        val result = validator.validateAndCleanValueForClaim(claim, "allowed")
        assertTrue(result.isPresent)
        assertEquals("allowed", result.get())
    }

    @Test
    fun `validateAndCleanValueForClaim - Accepts any value when allowedValues is null`() {
        val claim = mockClaim(allowedValues = null)
        val result = validator.validateAndCleanValueForClaim(claim, "anything")
        assertTrue(result.isPresent)
        assertEquals("anything", result.get())
    }

    // --- validateAndCleanStringForClaim ---

    @Test
    fun `validateAndCleanStringForClaim - Returns empty Optional for blank string`() {
        val claim = mockClaim()
        val result = validator.validateAndCleanStringForClaim(claim, "   ")
        assertTrue(result.isEmpty)
    }

    @Test
    fun `validateAndCleanStringForClaim - Trims whitespace on STRING claims`() {
        val claim = mockClaim(dataType = STRING)
        val result = validator.validateAndCleanStringForClaim(claim, "  hello  ")
        assertTrue(result.isPresent)
        assertEquals("hello", result.get())
    }

    // --- validateEmailForClaim ---

    @Test
    fun `validateEmailForClaim - Accepts valid email`() {
        val result = validator.validateEmailForClaim("user@example.com")
        assertTrue(result.isPresent)
        assertEquals("user@example.com", result.get())
    }

    @Test
    fun `validateEmailForClaim - Throws on missing @`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_email") {
            validator.validateEmailForClaim("userexample.com")
        }
    }

    @Test
    fun `validateEmailForClaim - Throws on empty local part`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_email") {
            validator.validateEmailForClaim("@example.com")
        }
    }

    @Test
    fun `validateEmailForClaim - Throws on empty domain`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_email") {
            validator.validateEmailForClaim("user@")
        }
    }

    // --- validateDateForClaim ---

    @Test
    fun `validateDateForClaim - Accepts valid date`() {
        val result = validator.validateDateForClaim("2024-01-15")
        assertTrue(result.isPresent)
        assertEquals("2024-01-15", result.get())
    }

    @Test
    fun `validateDateForClaim - Throws on invalid date format`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_date") {
            validator.validateDateForClaim("15/01/2024")
        }
    }

    // --- validateTimeZoneForClaim ---

    @Test
    fun `validateTimeZoneForClaim - Accepts valid timezone`() {
        val result = validator.validateTimeZoneForClaim("Europe/Paris")
        assertTrue(result.isPresent)
        assertEquals("Europe/Paris", result.get())
    }

    @Test
    fun `validateTimeZoneForClaim - Throws on invalid timezone`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_time_zone") {
            validator.validateTimeZoneForClaim("Not/A/Timezone")
        }
    }
}

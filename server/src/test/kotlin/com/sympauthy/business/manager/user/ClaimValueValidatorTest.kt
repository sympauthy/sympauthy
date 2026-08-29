package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.util.assertThrowsLocalizedException
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ClaimValueValidatorTest {

    @InjectMockKs
    lateinit var validator: ClaimValueValidator

    /** A claim of the type the validator checks a value against; the rest is read per path. */
    private fun mockStringClaim(): Claim = mockk {
        every { dataType } returns STRING
    }

    // --- validateAndCleanValueForClaim ---

    @Test
    fun `validateAndCleanValueForClaim - Returns empty Optional for null value`() {
        // A null value is never type-checked, but the allowed values are still consulted.
        val claim = mockk<Claim> { every { allowedValues } returns null }
        val result = validator.validateAndCleanValueForClaim(claim, null)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `validateAndCleanValueForClaim - Throws if value type does not match claim dataType`() {
        val claim = mockStringClaim()
        every { claim.id } returns "test_claim"
        assertThrowsLocalizedException("user.claim_value_validator.invalid_type") {
            validator.validateAndCleanValueForClaim(claim, 123)
        }
    }

    @Test
    fun `validateAndCleanValueForClaim - Throws if value not in allowedValues`() {
        val claim = mockStringClaim()
        every { claim.allowedValues } returns emptyList()
        assertThrowsLocalizedException("user.claim_value_validator.invalid_value") {
            validator.validateAndCleanValueForClaim(claim, "value")
        }
    }

    @Test
    fun `validateAndCleanValueForClaim - Accepts value present in allowedValues`() {
        val claim = mockStringClaim()
        every { claim.allowedValues } returns listOf("allowed")
        val result = validator.validateAndCleanValueForClaim(claim, "allowed")
        assertTrue(result.isPresent)
        assertEquals("allowed", result.get())
    }

    @Test
    fun `validateAndCleanValueForClaim - Accepts any value when allowedValues is null`() {
        val claim = mockStringClaim()
        every { claim.allowedValues } returns null
        val result = validator.validateAndCleanValueForClaim(claim, "anything")
        assertTrue(result.isPresent)
        assertEquals("anything", result.get())
    }

    // --- validateAndCleanStringForClaim ---

    @Test
    fun `validateAndCleanStringForClaim - Returns empty Optional for blank string`() {
        // A blank string is refused before the claim is looked at at all.
        val claim = mockk<Claim>()
        val result = validator.validateAndCleanStringForClaim(claim, "   ")
        assertTrue(result.isEmpty)
    }

    @Test
    fun `validateAndCleanStringForClaim - Trims whitespace on STRING claims`() {
        val claim = mockStringClaim()
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

    // --- validatePhoneNumberForClaim ---

    @Test
    fun `validatePhoneNumberForClaim - Accepts valid phone number`() {
        val result = validator.validatePhoneNumberForClaim("+15551234567")
        assertTrue(result.isPresent)
        assertEquals("+15551234567", result.get())
    }

    @Test
    fun `validatePhoneNumberForClaim - Accepts minimal phone number`() {
        val result = validator.validatePhoneNumberForClaim("+1")
        assertTrue(result.isPresent)
        assertEquals("+1", result.get())
    }

    @Test
    fun `validatePhoneNumberForClaim - Accepts max-length phone number`() {
        val result = validator.validatePhoneNumberForClaim("+123456789012345")
        assertTrue(result.isPresent)
        assertEquals("+123456789012345", result.get())
    }

    @Test
    fun `validatePhoneNumberForClaim - Throws on missing plus prefix`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_phone_number") {
            validator.validatePhoneNumberForClaim("15551234567")
        }
    }

    @Test
    fun `validatePhoneNumberForClaim - Throws on letters in number`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_phone_number") {
            validator.validatePhoneNumberForClaim("+1555abc4567")
        }
    }

    @Test
    fun `validatePhoneNumberForClaim - Throws on too many digits`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_phone_number") {
            validator.validatePhoneNumberForClaim("+1234567890123456")
        }
    }

    @Test
    fun `validatePhoneNumberForClaim - Throws on plus only`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_phone_number") {
            validator.validatePhoneNumberForClaim("+")
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

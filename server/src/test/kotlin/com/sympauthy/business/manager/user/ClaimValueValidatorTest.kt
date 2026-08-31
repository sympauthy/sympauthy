package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.util.assertThrowsLocalizedException
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType.NUMBER
import com.sympauthy.business.model.user.claim.ClaimDataType.PHONE_NUMBER
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

    /** The one type whose value is not exchanged as a string. */
    private fun mockNumberClaim(): Claim = mockk {
        every { dataType } returns NUMBER
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
    fun `validateAndCleanValueForClaim - Names the refused type the way the API publishes it`() {
        // phone-number in the configuration file, PHONE_NUMBER in Kotlin, phone_number to whoever reads this.
        val claim = mockk<Claim> {
            every { dataType } returns PHONE_NUMBER
            every { id } returns "phone_number"
        }
        val exception = assertThrowsLocalizedException("user.claim_value_validator.invalid_type") {
            validator.validateAndCleanValueForClaim(claim, true)
        }
        assertEquals("phone_number", exception.values["type"])
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

    @Test
    fun `validateAndCleanValueForClaim - Reads a number submitted as a string as a Long`() {
        val claim = mockNumberClaim()
        every { claim.allowedValues } returns null
        val result = validator.validateAndCleanValueForClaim(claim, "42")
        assertEquals(42L, result.get())
    }

    @Test
    fun `validateAndCleanValueForClaim - Reads a number submitted as a number as a Long`() {
        val claim = mockNumberClaim()
        every { claim.allowedValues } returns null
        val result = validator.validateAndCleanValueForClaim(claim, 42)
        assertEquals(42L, result.get())
    }

    @Test
    fun `validateAndCleanValueForClaim - Matches an allowed number however it was submitted`() {
        // The configuration holds longs, so neither representation matches until the value is cleaned.
        val claim = mockNumberClaim()
        every { claim.allowedValues } returns listOf(42L)
        assertEquals(42L, validator.validateAndCleanValueForClaim(claim, "42").get())
        assertEquals(42L, validator.validateAndCleanValueForClaim(claim, 42).get())
    }

    @Test
    fun `validateAndCleanValueForClaim - Throws if a number is not one of the allowed values`() {
        val claim = mockNumberClaim()
        every { claim.allowedValues } returns listOf(42L)
        assertThrowsLocalizedException("user.claim_value_validator.invalid_value") {
            validator.validateAndCleanValueForClaim(claim, 43)
        }
    }

    @Test
    fun `validateAndCleanValueForClaim - Consults the allowed values with the cleaned value`() {
        val claim = mockStringClaim()
        every { claim.allowedValues } returns listOf("allowed")
        val result = validator.validateAndCleanValueForClaim(claim, "  allowed  ")
        assertEquals("allowed", result.get())
    }

    @Test
    fun `validateAndCleanValueForClaim - Clears a claim carrying allowed values on a blank value`() {
        // A blank value clears the claim, and what is not being stored has nothing to be allowed against.
        // It is cleared before the claim's type is looked at, which is why none is stubbed here.
        val claim = mockk<Claim> { every { allowedValues } returns listOf("allowed") }
        assertTrue(validator.validateAndCleanValueForClaim(claim, "   ").isEmpty)
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

    @Test
    fun `validateAndCleanStringForClaim - Trims whitespace on NUMBER claims`() {
        val claim = mockNumberClaim()
        val result = validator.validateAndCleanStringForClaim(claim, "  42  ")
        assertTrue(result.isPresent)
        assertEquals(42L, result.get())
    }

    // --- validateAndCleanNumberForClaim ---

    @Test
    fun `validateAndCleanNumberForClaim - Accepts a negative whole number`() {
        val result = validator.validateAndCleanNumberForClaim("-7")
        assertEquals(-7L, result.get())
    }

    @Test
    fun `validateAndCleanNumberForClaim - Throws on a fraction`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_number") {
            validator.validateAndCleanNumberForClaim(1.5)
        }
    }

    @Test
    fun `validateAndCleanNumberForClaim - Throws past the range of a Long`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_number") {
            validator.validateAndCleanNumberForClaim("9223372036854775808")
        }
    }

    @Test
    fun `validateAndCleanNumberForClaim - Throws on a value that is not a number`() {
        assertThrowsLocalizedException("user.claim_value_validator.invalid_number") {
            validator.validateAndCleanNumberForClaim(true)
        }
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

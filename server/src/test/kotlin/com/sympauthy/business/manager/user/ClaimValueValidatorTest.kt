package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.util.assertThrowsLocalizedException
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType.STRING
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ClaimValueValidatorTest {

    @InjectMockKs
    lateinit var validator: ClaimValueValidator

    @Test
    fun `validateAndCleanValueForClaim - Throws if not allowed values`() {
        val claim = mockk<Claim> {
            every { dataType } returns STRING
            every { allowedValues } returns emptyList()
        }
        assertThrowsLocalizedException("user.claim_value_validator.invalid_value") {
            validator.validateAndCleanValueForClaim(claim, "value")
        }
    }
}

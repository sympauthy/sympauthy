package com.sympauthy.api.mapper

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.ClaimValueValidator
import com.sympauthy.business.model.user.claim.Claim
import io.micronaut.http.HttpStatus.BAD_REQUEST
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class CollectedClaimUpdateMapperTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var claimValueValidator: ClaimValueValidator

    @InjectMockKs
    lateinit var mapper: CollectedClaimUpdateMapper

    /** A claim this deployment declares under [id], whose value the validator accepts. */
    private fun accept(id: String, value: Any?, cleaned: Any): Claim = declare(id).also {
        every { claimValueValidator.validateAndCleanValueForClaim(it, value) } returns Optional.of(cleaned)
    }

    /**
     * A claim this deployment declares under [id], whose value the validator refuses with [detailsId]
     * and the claim as a value to interpolate — as
     * [com.sympauthy.business.manager.user.ClaimValueValidator] throws it.
     *
     * Its id is stubbed here rather than in [declare] because the mapper only reads the id of a claim
     * it refuses, and a stub nothing reads fails the run.
     */
    private fun refuse(id: String, value: Any?, detailsId: String): Claim = declare(id).also {
        every { it.id } returns id
        every { claimValueValidator.validateAndCleanValueForClaim(it, value) } throws
            recoverableBusinessExceptionOf(detailsId, "description.$detailsId", "claim" to id)
    }

    private fun declare(id: String): Claim = mockk<Claim>().also {
        every { claimManager.findByIdOrNull(id) } returns it
    }

    @Test
    fun `toUpdates - Return an update per claim whose value passed validation`() {
        val email = accept("email", "someone@example.com", "someone@example.com")
        val name = accept("name", " Ada ", "Ada")

        val updates = mapper.toUpdates(linkedMapOf("email" to "someone@example.com", "name" to " Ada "))

        assertEquals(listOf(email, name), updates.map { it.claim })
        // The cleaned value, not the one submitted: the validator owns what is stored.
        assertEquals(listOf("someone@example.com", "Ada"), updates.map { it.value?.get() })
    }

    @Test
    fun `toUpdates - Ignore a claim this deployment does not declare`() {
        val email = accept("email", "someone@example.com", "someone@example.com")
        every { claimManager.findByIdOrNull("unknown") } returns null

        val updates = mapper.toUpdates(linkedMapOf("unknown" to "value", "email" to "someone@example.com"))

        assertEquals(listOf(email), updates.map { it.claim })
    }

    @Test
    fun `toUpdates - Report every claim that was refused, not the first`() {
        refuse("email", 42, "user.claim_value_validator.invalid_type")
        refuse("birthdate", "31/02/2026", "user.claim_value_validator.invalid_date")
        accept("name", "Ada", "Ada")

        val exception = assertThrows<LocalizedHttpException> {
            mapper.toUpdates(linkedMapOf("email" to 42, "birthdate" to "31/02/2026", "name" to "Ada"))
        }

        assertEquals(listOf("email", "birthdate"), exception.propertyErrors.keys.toList())
        assertEquals(
            listOf("user.claim_value_validator.invalid_type", "user.claim_value_validator.invalid_date"),
            exception.propertyErrors.values.map { it.detailsId }
        )
        // The failure itself, so the values it carries reach the reader with it.
        assertEquals("email", exception.propertyErrors.getValue("email").values["claim"])
    }

    @Test
    fun `toUpdates - Refuse the payload as a bad request the caller can correct`() {
        refuse("email", 42, "user.claim_value_validator.invalid_type")

        val exception = assertThrows<LocalizedHttpException> { mapper.toUpdates(mapOf("email" to 42)) }

        assertEquals(BAD_REQUEST, exception.status)
        assertEquals("flow.claims.invalid", exception.detailsId)
        assertEquals("description.flow.claims.invalid", exception.descriptionId)
        assertTrue(exception.recoverable)
    }
}

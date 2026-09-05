package com.sympauthy.exception.mapper

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.config.model.EnabledFeaturesConfig
import com.sympauthy.exception.LocalizedException
import io.micronaut.context.StaticMessageSource
import io.micronaut.http.HttpStatus.BAD_REQUEST
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class LocalizedErrorMapperTest {

    /**
     * The messages the cases below render, in the shape the bundle writes them: a technical one for
     * an operator, a `description.` one for the end-user, and a placeholder on each half of the pair
     * that carries values.
     */
    private val messageSource = StaticMessageSource()
        .addMessage(Locale.US, "flow.claims.invalid", "One or more of the submitted claims did not pass validation.")
        .addMessage(
            Locale.US, "description.flow.claims.invalid",
            "One or more of the values you submitted were refused."
        )
        .addMessage(
            Locale.US, "user.claim_value_validator.invalid_type",
            "Invalid type, expected value to be of type {type}."
        )
        .addMessage(
            Locale.US, "description.user.claim_value_validator.invalid_type",
            "The value provided is not of the expected type ({type})."
        )
        .addMessage(
            Locale.US, "user.claim_value_validator.invalid_date",
            "Expected date to be formatted as YYYY-MM-DD."
        )
        .addMessage(
            Locale.US, "description.user.claim_value_validator.invalid_date",
            "Please provide a date formatted as YYYY-MM-DD."
        )
        .addMessage(Locale.US, "user.not_found", "No user with id {id}.")

    private fun mapperOf(printDetailsInError: Boolean) = LocalizedErrorMapper(
        messageSource = messageSource,
        featuresConfig = EnabledFeaturesConfig(
            allowAccessToClientWithoutScope = false,
            emailValidation = false,
            grantUnhandledScopes = false,
            printDetailsInError = printDetailsInError
        )
    )

    private val mapper = mapperOf(printDetailsInError = false)

    /**
     * The failure of a claim, as [com.sympauthy.business.manager.user.ClaimValueValidator] throws it:
     * a code, the end-user's version of it, and the values both of them interpolate.
     */
    private fun invalidType(type: String) = recoverableBusinessExceptionOf(
        "user.claim_value_validator.invalid_type",
        "description.user.claim_value_validator.invalid_type",
        "type" to type
    )

    private fun invalidDate() = recoverableBusinessExceptionOf(
        "user.claim_value_validator.invalid_date",
        "description.user.claim_value_validator.invalid_date"
    )

    private fun claimsInvalid(propertyErrors: Map<String, LocalizedException>) =
        LocalizedHttpException(
            status = BAD_REQUEST,
            recoverable = true,
            detailsId = "flow.claims.invalid",
            descriptionId = "description.flow.claims.invalid",
            propertyErrors = propertyErrors
        )

    @Test
    fun `toLocalizedError - Report one property per refused claim, in the order they were refused`() {
        val error = mapper.toLocalizedError(
            claimsInvalid(linkedMapOf("email" to invalidType("email"), "birthdate" to invalidDate())),
            Locale.US
        )

        assertEquals(listOf("email", "birthdate"), error.properties.map { it.path })
        assertEquals(
            listOf("user.claim_value_validator.invalid_type", "user.claim_value_validator.invalid_date"),
            error.properties.map { it.errorCode }
        )
    }

    @Test
    fun `toLocalizedError - Interpolate the values the refused property carries`() {
        val error = mapper.toLocalizedError(claimsInvalid(mapOf("email" to invalidType("email"))), Locale.US)

        // The value the validator computed and the entry used to drop, reaching the reader as a word
        // rather than as {type}.
        assertEquals(
            "The value provided is not of the expected type (email).",
            error.properties.single().description
        )
    }

    @Test
    fun `toLocalizedError - Leave a property naming no description without one`() {
        val error = mapper.toLocalizedError(
            claimsInvalid(mapOf("email" to businessExceptionOf("user.not_found", "id" to "42"))),
            Locale.US
        )

        // Not the generic sentence the error itself falls back to: it answers for the response.
        assertNull(error.properties.single().description)
        assertEquals("user.not_found", error.properties.single().errorCode)
    }

    @Test
    fun `toLocalizedError - Report the technical message of a property only when the details are printed`() {
        val exception = claimsInvalid(mapOf("email" to invalidType("email")))

        assertNull(mapper.toLocalizedError(exception, Locale.US).properties.single().details)
        assertEquals(
            "Invalid type, expected value to be of type email.",
            mapperOf(printDetailsInError = true).toLocalizedError(exception, Locale.US).properties.single().details
        )
    }

    @Test
    fun `toLocalizedError - Report no property for a failure refusing none`() {
        val error = mapper.toLocalizedError(businessExceptionOf("user.not_found", "id" to "42"), Locale.US)

        assertTrue(error.properties.isEmpty())
        assertEquals("user.not_found", error.errorCode)
    }
}

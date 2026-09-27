package com.sympauthy.util

import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.exception.localizedExceptionOf
import io.micronaut.context.StaticMessageSource
import io.micronaut.data.exceptions.DataIntegrityViolationException
import io.micronaut.data.exceptions.EntityExistsException
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

class ExceptionUtilTest {

    /**
     * A message source holding the one message each case renders, with a placeholder per value the
     * exception carries.
     */
    private fun messageSource(code: String) = StaticMessageSource().addMessage(
        Locale.US, code, "Audience {audience} does not exist. Available audiences: {availableAudiences}."
    )

    @Test
    fun `getKeyAndLocalizedMessage - Interpolates the values of a ConfigurationException`() {
        val exception = configExceptionOf(
            "claims.email.audience", "config.claim.audience.not_found",
            "audience" to "nonexistent-audience",
            "availableAudiences" to "default, admin"
        )

        val (key, message) = exception.getKeyAndLocalizedMessage(
            messageSource("config.claim.audience.not_found")
        )

        assertEquals("claims.email.audience", key)
        assertEquals(
            "Audience nonexistent-audience does not exist. Available audiences: default, admin.",
            message
        )
    }

    @Test
    fun `getKeyAndLocalizedMessage - Interpolates the values of a LocalizedException`() {
        val exception = localizedExceptionOf(
            "config.claim.audience.not_found",
            "key" to "claims.email.audience",
            "audience" to "nonexistent-audience",
            "availableAudiences" to "default, admin"
        )

        val (key, message) = exception.getKeyAndLocalizedMessage(
            messageSource("config.claim.audience.not_found")
        )

        assertEquals("claims.email.audience", key)
        assertEquals(
            "Audience nonexistent-audience does not exist. Available audiences: default, admin.",
            message
        )
    }

    @Test
    fun `isRowAlreadyThere - Reads the refusal of a key that is already taken`() {
        val refused = EntityExistsException("duplicate key", R2dbcDataIntegrityViolationException("23505"))

        assertTrue(refused.isRowAlreadyThere())
    }

    @Test
    fun `isRowAlreadyThere - Reads a refusal something above the repository wrapped`() {
        val wrapped = IllegalStateException(
            "transaction failed", RuntimeException("query failed", EntityExistsException("duplicate key"))
        )

        assertTrue(wrapped.isRowAlreadyThere())
    }

    /**
     * The narrowing this predicate exists for: the driver raises one exception for the whole class of
     * integrity failures, and only the key already taken is the one a manager translates.
     */
    @Test
    fun `isRowAlreadyThere - Reads no taken key in an integrity failure of another kind`() {
        val violated = DataIntegrityViolationException(
            "foreign key", R2dbcDataIntegrityViolationException("23503")
        )

        assertFalse(violated.isRowAlreadyThere())
    }

    @Test
    fun `isRowAlreadyThere - Reads no taken key in a failure no constraint refused`() {
        assertFalse(IllegalStateException("connection closed").isRowAlreadyThere())
    }

    /** A chain deeper than the walk answers false rather than holding it open. */
    @Test
    fun `isRowAlreadyThere - Stops walking a chain that has no end`() {
        val cycling = IllegalStateException("first")
        cycling.initCause(IllegalStateException("second", cycling))

        assertFalse(cycling.isRowAlreadyThere())
    }
}

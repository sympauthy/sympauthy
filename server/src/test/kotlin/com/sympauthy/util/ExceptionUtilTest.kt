package com.sympauthy.util

import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.exception.localizedExceptionOf
import io.micronaut.context.StaticMessageSource
import io.micronaut.context.i18n.ResourceBundleMessageSource
import org.junit.jupiter.api.Assertions.assertEquals
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

    // --- getKeyAndLocalizedMessage ---

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
    fun `getKeyAndLocalizedMessage - Interpolates into the message the error bundle ships`() {
        val exception = configExceptionOf(
            "claims.email.audience", "config.claim.audience.not_found",
            "audience" to "nonexistent-audience",
            "availableAudiences" to "default, admin"
        )

        val (_, message) = exception.getKeyAndLocalizedMessage(
            ResourceBundleMessageSource("error_messages", DEFAULT_LOCALE)
        )

        assertTrue(message!!.contains("nonexistent-audience"), message)
        assertTrue(message.contains("default, admin"), message)
    }
}

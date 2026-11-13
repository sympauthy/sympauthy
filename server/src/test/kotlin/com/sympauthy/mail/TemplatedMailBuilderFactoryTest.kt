package com.sympauthy.mail

import io.micronaut.context.i18n.ResourceBundleMessageSource
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class TemplatedMailBuilderFactoryTest {

    private val brand = mapOf("displayName" to "Acme Corp")

    private val mailBrandResolver = mockk<MailBrandResolver> {
        every { resolve() } returns brand
        every { displayName } returns "Acme Corp"
    }

    private val factory = TemplatedMailBuilderFactory(
        defaultFrom = null,
        messageSource = ResourceBundleMessageSource("mail_messages"),
        mailBrandResolver = mailBrandResolver
    )

    @Test
    fun `builder - Draws the mail in the brand at the time it is sent, over what it was queued with`() {
        val model = factory.builder(
            template = "mails/validation_code",
            locale = Locale.US,
            parameters = mapOf("code" to "123456", "displayName" to "Acme", "brand" to "queued")
        ).model

        assertEquals("123456", model["code"])
        assertEquals("Acme Corp", model["displayName"])
        assertEquals(brand, model["brand"])
    }

    @Test
    fun `builder - States the minutes left at the time the mail is sent, rounded up`() {
        val model = factory.builder(
            template = "mails/validation_code",
            locale = Locale.US,
            parameters = mapOf("expiryMinutes" to "15"),
            expirationDate = LocalDateTime.now().plusMinutes(2).plusSeconds(30)
        ).model

        assertEquals("3", model["expiryMinutes"])
    }

    @Test
    fun `builder - States no minutes for a mail that does not expire`() {
        val model = factory.builder(template = "mails/validation_code", locale = Locale.US).model

        assertFalse("expiryMinutes" in model)
    }
}

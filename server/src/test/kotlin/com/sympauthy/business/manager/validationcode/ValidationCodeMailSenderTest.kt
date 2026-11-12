package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.code.ValidationCodeReason.EMAIL_CLAIM
import com.sympauthy.business.model.code.ValidationCodeReason.RESET_PASSWORD
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.DisabledUIConfig
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.ThemeConfig
import com.sympauthy.config.model.UIConfig
import com.sympauthy.mail.MailBrandResolver
import com.sympauthy.mail.MailQueue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ValidationCodeMailSenderTest {

    private val mailQueue = mockk<MailQueue>()

    private val claim = mockk<CollectedClaim> {
        every { this@mockk.claim.id } returns EMAIL.claim
        every { value } returns "person@example.com"
    }

    private fun codeFor(vararg reasons: ValidationCodeReason) = mockk<ValidationCode> {
        every { this@mockk.code } returns "123456"
        every { this@mockk.reasons } returns reasons.toList()
        every { expirationDate } returns LocalDateTime.now().plusMinutes(5)
    }

    private fun senderUnder(config: UIConfig) =
        ValidationCodeMailSender(mailQueue, mockk(), MailBrandResolver(mockk(), mockk(), config, mockk()))

    private fun configNaming(displayName: String?) = EnabledUIConfig(
        displayName = displayName,
        icon = null,
        logo = null,
        logoDark = null,
        theme = ThemeConfig.DEFAULT
    )

    private suspend fun assertSentUnder(config: UIConfig, expected: String) {
        coEvery { mailQueue.send(any(), any(), any(), any(), any(), any()) } returns Unit

        senderUnder(config).sendValidationCode(mockk(), claim, codeFor(EMAIL_CLAIM))

        coVerify {
            mailQueue.send(
                template = any(),
                locale = any(),
                receiver = "person@example.com",
                subjectKey = any(),
                parameters = match { it["displayName"] == expected },
                maxAge = any()
            )
        }
    }

    @Test
    fun `sendValidationCode - Signs the mail with the configured display name`() = runTest {
        assertSentUnder(configNaming("Acme"), "Acme")
    }

    @Test
    fun `sendValidationCode - Signs the mail as SympAuthy where no display name is configured`() = runTest {
        assertSentUnder(configNaming(null), "SympAuthy")
    }

    @Test
    fun `sendValidationCode - Still sends the mail where the ui section failed`() = runTest {
        assertSentUnder(DisabledUIConfig(emptyList()), "SympAuthy")
    }

    private suspend fun assertSentWith(code: ValidationCode, template: String) {
        coEvery { mailQueue.send(any(), any(), any(), any(), any(), any()) } returns Unit

        senderUnder(configNaming(null)).sendValidationCode(mockk(), claim, code)

        coVerify {
            mailQueue.send(
                template = "mails/$template",
                locale = any(),
                receiver = "person@example.com",
                subjectKey = "mail.$template.subject",
                parameters = match { it["code"] == "123456" && it["expiryMinutes"] == "5" },
                maxAge = any()
            )
        }
    }

    @Test
    fun `sendValidationCode - Sends the validation code mail for an email claim`() = runTest {
        assertSentWith(codeFor(EMAIL_CLAIM), "validation_code")
    }

    @Test
    fun `sendValidationCode - Sends the reset password mail for a code that resets the password`() = runTest {
        assertSentWith(codeFor(EMAIL_CLAIM, RESET_PASSWORD), "reset_password")
    }
}

package com.sympauthy.mail

import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.code.ValidationCodeReason.EMAIL_CLAIM
import com.sympauthy.business.model.code.ValidationCodeReason.RESET_PASSWORD
import com.sympauthy.business.model.user.CollectedClaim
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ValidationCodeMailSenderTest {

    private val mailQueue = mockk<MailQueue>()

    private val sender = ValidationCodeMailSender(mailQueue, mockk())

    private val claim = mockk<CollectedClaim> {
        every { this@mockk.claim.id } returns EMAIL.claim
        every { value } returns "person@example.com"
    }

    private fun codeFor(vararg reasons: ValidationCodeReason) = mockk<ValidationCode> {
        every { this@mockk.code } returns "123456"
        every { this@mockk.reasons } returns reasons.toList()
        every { expirationDate } returns LocalDateTime.now().plusMinutes(5)
    }

    private suspend fun assertSentWith(code: ValidationCode, template: String) {
        coEvery { mailQueue.send(any(), any(), any(), any(), any(), any()) } returns Unit

        sender.sendValidationCode(mockk(), claim, code)

        coVerify {
            mailQueue.send(
                template = "mails/$template",
                locale = any(),
                receiver = "person@example.com",
                subjectKey = "mail.$template.subject",
                parameters = mapOf("code" to "123456"),
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

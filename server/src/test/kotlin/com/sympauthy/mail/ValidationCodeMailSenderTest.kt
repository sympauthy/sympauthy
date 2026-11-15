package com.sympauthy.mail

import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.code.ValidationCodeReason.EMAIL_CLAIM
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

    @Test
    fun `sendValidationCode - Sends the claim validation mail for an email claim`() = runTest {
        val code = mockk<ValidationCode> {
            every { this@mockk.code } returns "123456"
            every { reasons } returns listOf(EMAIL_CLAIM)
            every { expirationDate } returns LocalDateTime.now().plusMinutes(5)
        }
        coEvery { mailQueue.send(any(), any(), any(), any(), any()) } returns Unit

        sender.sendValidationCode(mockk(), claim, code)

        coVerify {
            mailQueue.send(
                mail = Mail.CLAIM_VALIDATION,
                locale = any(),
                receiver = "person@example.com",
                parameters = mapOf("code" to "123456"),
                maxAge = any()
            )
        }
    }
}

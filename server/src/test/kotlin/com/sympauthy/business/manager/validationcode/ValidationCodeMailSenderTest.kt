package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.manager.mail.MailQueue
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.ThemeConfig
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

    private val code = mockk<ValidationCode> {
        every { this@mockk.code } returns "123456"
        every { expirationDate } returns LocalDateTime.now().plusMinutes(5)
    }

    private fun sender(displayName: String?) = ValidationCodeMailSender(
        mailQueue,
        mockk(),
        EnabledUIConfig(
            displayName = displayName,
            icon = null,
            logo = null,
            logoDark = null,
            theme = ThemeConfig.DEFAULT
        )
    )

    private suspend fun assertSentUnder(displayName: String?, expected: String) {
        coEvery { mailQueue.send(any(), any(), any(), any(), any(), any()) } returns Unit

        sender(displayName).sendValidationCode(mockk(), claim, code)

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
        assertSentUnder("Acme", "Acme")
    }

    @Test
    fun `sendValidationCode - Signs the mail as SympAuthy where no display name is configured`() = runTest {
        assertSentUnder(null, "SympAuthy")
    }
}

package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.ValidationCodeMapper
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.data.model.ValidationCodeEntity
import com.sympauthy.data.repository.ValidationCodeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockKExtension::class)
class ValidationCodeGeneratorTest {

    @MockK
    lateinit var validationCodeRepository: ValidationCodeRepository

    @MockK
    lateinit var validationCodeMapper: ValidationCodeMapper

    @MockK
    lateinit var randomGenerator: RandomGenerator

    @MockK
    lateinit var advancedConfig: AdvancedConfig

    @SpyK
    @InjectMockKs
    lateinit var generator: ValidationCodeGenerator

    @Test
    fun `validationCodeFormat - Generate X digits padded with 0 format`() {
        every { generator.validationCodeLength } returns 6
        assertEquals("%06d", generator.validationCodeFormat)
    }

    @Test
    fun `validationCodeBound - Generate upper bound according to code length`() {
        every { generator.validationCodeLength } returns 6
        assertEquals(100_000, generator.validationCodeBound)
    }

    @Test
    fun `generateValidationCode - Draws another code where the session already holds the one drawn`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<InteractiveFlowSession> { every { id } returns sessionId }
        val stored = mockk<ValidationCodeEntity>()
        val validationCode = mockk<ValidationCode>()
        every { generator.generateCode() } returnsMany listOf("000345", "000678")
        every { generator.getExpirationDate(session, any()) } returns LocalDateTime.MAX
        every { generator.getResendDate(any()) } returns null
        coEvery {
            validationCodeRepository.insertUnlessCodeHeld(
                "000345", any(), any(), any(), sessionId, any(), any(), any()
            )
        } returns 0
        coEvery {
            validationCodeRepository.insertUnlessCodeHeld(
                "000678", any(), any(), any(), sessionId, any(), any(), any()
            )
        } returns 1
        coEvery { validationCodeRepository.findBySessionIdAndCode(sessionId, "000678") } returns stored
        every { validationCodeMapper.toValidationCode(stored) } returns validationCode

        val result = generator.generateValidationCode(
            user = mockk { every { id } returns UUID.randomUUID() },
            session = session,
            media = ValidationCodeMedia.EMAIL,
            reasons = emptyList()
        )

        assertSame(validationCode, result)
    }

    @Test
    fun `generateValidationCode - Throws once the session held every code drawn`() = runTest {
        val session = mockk<InteractiveFlowSession> { every { id } returns UUID.randomUUID() }
        every { generator.generateCode() } returns "000345"
        every { generator.getExpirationDate(session, any()) } returns LocalDateTime.MAX
        every { generator.getResendDate(any()) } returns null
        coEvery {
            validationCodeRepository.insertUnlessCodeHeld(any(), any(), any(), any(), any(), any(), any(), any())
        } returns 0

        val exception = assertThrows<BusinessException> {
            generator.generateValidationCode(
                user = mockk { every { id } returns UUID.randomUUID() },
                session = session,
                media = ValidationCodeMedia.EMAIL,
                reasons = emptyList()
            )
        }

        assertEquals("validationcode.every_draw_held", exception.detailsId)
        coVerify(exactly = ValidationCodeGenerator.MAX_ATTEMPTS) {
            validationCodeRepository.insertUnlessCodeHeld(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `generateCode - Generate X digits validation code`() {
        every { generator.validationCodeLength } returns 6
        every { randomGenerator.generateInt(any(), any()) } returns 345
        val code = generator.generateCode()
        assertEquals("000345", code)
    }
}

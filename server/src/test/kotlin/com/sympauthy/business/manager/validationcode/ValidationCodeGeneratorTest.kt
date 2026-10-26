package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.ValidationCodeMapper
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.data.repository.ValidationCodeRepository
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.InteractiveFlowSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.extension.ExtendWith

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
    fun `generateValidationCode - Throws the last insert failure as the cause once every attempt failed`() =
        runTest {
            val session = mockk<InteractiveFlowSession>()
            every { generator.generateCode() } returns "000345"
            every { generator.getExpirationDate(session, any()) } returns LocalDateTime.MAX
            every { generator.getResendDate(any()) } returns null
            val failure = IllegalStateException("column does not exist")
            coEvery { validationCodeRepository.save(any()) } throws failure

            val thrown = assertThrows<IllegalStateException> {
                generator.generateValidationCode(
                    user = mockk { every { id } returns UUID.randomUUID() },
                    session = session.also { every { it.id } returns UUID.randomUUID() },
                    media = ValidationCodeMedia.EMAIL,
                    reasons = emptyList()
                )
            }

            assertSame(failure, thrown.cause)
        }

    @Test
    fun `generateCode - Generate X digits validation code`() {
        every { generator.validationCodeLength } returns 6
        every { randomGenerator.generateInt(any(), any()) } returns 345
        val code = generator.generateCode()
        assertEquals("000345", code)
    }
}

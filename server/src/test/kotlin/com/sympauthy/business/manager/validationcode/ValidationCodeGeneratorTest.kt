package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.ValidationCodeMapper
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.data.model.ValidationCodeEntity
import com.sympauthy.data.repository.ValidationCodeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
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
    fun `generateValidationCode - Draws outside the codes the session already holds`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<InteractiveFlowSession> { every { id } returns sessionId }
        val validationCode = mockk<ValidationCode>()
        every { generator.validationCodeLength } returns 6
        every { generator.getExpirationDate(session, any()) } returns LocalDateTime.MAX
        every { generator.getResendDate(any()) } returns null
        coEvery { validationCodeRepository.findBySessionId(sessionId) } returns listOf(heldCode(sessionId, "000000"))
        every { randomGenerator.generateInt(0, 99_999) } returns 0
        val saved = slot<ValidationCodeEntity>()
        coEvery { validationCodeRepository.save(capture(saved)) } answers { saved.captured }
        every { validationCodeMapper.toValidationCode(any()) } returns validationCode

        val result = generator.generateValidationCode(
            user = mockk { every { id } returns UUID.randomUUID() },
            session = session,
            media = ValidationCodeMedia.EMAIL,
            reasons = emptyList()
        )

        assertSame(validationCode, result)
        assertEquals("000001", saved.captured.code)
    }

    @Test
    fun `generateCode - Steps over each held code at or below the drawn rank`() {
        every { generator.validationCodeLength } returns 6
        every { randomGenerator.generateInt(0, 99_997) } returns 2
        val code = generator.generateCode(listOf("000005", "000000", "000002"))
        assertEquals("000004", code)
    }

    @Test
    fun `generateCode - Ignores a held code of another length`() {
        every { generator.validationCodeLength } returns 6
        every { randomGenerator.generateInt(0, 100_000) } returns 2
        val code = generator.generateCode(listOf("00002"))
        assertEquals("000002", code)
    }

    @Test
    fun `generateCode - Throws once the session holds every code`() {
        every { generator.validationCodeLength } returns 1
        assertThrows<IllegalStateException> { generator.generateCode(listOf("0")) }
    }

    @Test
    fun `generateCode - Generate X digits validation code`() {
        every { generator.validationCodeLength } returns 6
        every { randomGenerator.generateInt(any(), any()) } returns 345
        val code = generator.generateCode(emptyList())
        assertEquals("000345", code)
    }

    private fun heldCode(sessionId: UUID, code: String) = ValidationCodeEntity(
        code = code,
        userId = UUID.randomUUID(),
        media = ValidationCodeMedia.EMAIL.name,
        reasons = emptyArray(),
        sessionId = sessionId,
        resendDate = null,
        expirationDate = LocalDateTime.MAX
    )
}

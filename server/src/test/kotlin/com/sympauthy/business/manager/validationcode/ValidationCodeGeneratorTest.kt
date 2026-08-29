package com.sympauthy.business.manager.validationcode

import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.ValidationCodeMapper
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.data.repository.ValidationCodeRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
    fun `generateCode - Generate X digits validation code`() {
        every { generator.validationCodeLength } returns 6
        every { randomGenerator.generateInt(any(), any()) } returns 345
        val code = generator.generateCode()
        assertEquals("000345", code)
    }
}

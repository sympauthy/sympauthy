package com.sympauthy.business.manager.password

import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.HashConfig
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.Executors

@ExtendWith(MockKExtension::class)
class PasswordHashGeneratorTest {

    @Suppress("unused")
    private val executorService = Executors.newSingleThreadExecutor()

    @MockK
    lateinit var advancedConfig: EnabledAdvancedConfig

    @InjectMockKs
    lateinit var generator: PasswordHashGenerator

    @Test
    fun `hash`() = runTest {
        every { advancedConfig.hashConfig } returns HashConfig(
            costParameter = 1024,
            blockSize = 8,
            parallelizationParameter = 1,
            saltLengthInBytes = 0,
            keyLengthInBytes = 32
        )

        val hash = generator.hash(
            "password",
            "test".toByteArray(Charsets.UTF_16)
        )

        assertEquals(32, hash.size)
    }
}

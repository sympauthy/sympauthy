package com.sympauthy.business.manager.password

import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.HashConfig
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.opentelemetry.api.OpenTelemetry
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

    @SpyK
    var authenticationMetrics = AuthenticationMetrics(OpenTelemetry.noop())

    @SpyK
    var customSpanTracer = CustomSpanTracer(OpenTelemetry.noop())

    @InjectMockKs
    lateinit var generator: PasswordHashGenerator

    @Test
    fun hash() = runTest {
        every { advancedConfig.hashConfig } returns HashConfig(
            costParameter = 1024,
            blockSize = 8,
            parallelizationParameter = 1,
            saltLengthInBytes = 0,
            keyLengthInBytes = 32
        )

        val hash = generator.hash(
            "password",
            "test".toByteArray(Charsets.UTF_16),
            PasswordHashGenerator.VERIFY
        )

        assertEquals(32, hash.size)
    }
}

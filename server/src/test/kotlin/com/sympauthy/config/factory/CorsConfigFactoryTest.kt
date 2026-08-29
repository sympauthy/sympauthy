package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.model.DisabledCorsConfig
import com.sympauthy.config.model.EnabledCorsConfig
import com.sympauthy.config.parsing.CorsConfigParser
import com.sympauthy.config.properties.CorsConfigurationProperties
import com.sympauthy.config.validation.CorsConfigValidator
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class CorsConfigFactoryTest {

    @SpyK
    var parser = ConfigParser()

    private fun factory(): CorsConfigFactory {
        return CorsConfigFactory(
            CorsConfigParser(parser),
            CorsConfigValidator()
        )
    }

    private fun properties(allowedHeaders: List<String>?): CorsConfigurationProperties {
        return mockk {
            every { this@mockk.allowedHeaders } returns allowedHeaders
        }
    }

    private fun errorMessages(config: DisabledCorsConfig): List<String> {
        return config.configurationErrors!!.map { it.message!! }
    }

    @Test
    fun `Absent section produces an EnabledCorsConfig with no additional header`() {
        val result = factory().provideCorsConfig(properties(null))

        assertInstanceOf(EnabledCorsConfig::class.java, result)
        assertEquals(emptyList<String>(), (result as EnabledCorsConfig).allowedHeaders)
    }

    @Test
    fun `Valid headers are parsed in the order they are declared`() {
        val result = factory().provideCorsConfig(properties(listOf("X-Requested-With", "X-Trace-Id")))

        assertInstanceOf(EnabledCorsConfig::class.java, result)
        assertEquals(listOf("X-Requested-With", "X-Trace-Id"), (result as EnabledCorsConfig).allowedHeaders)
    }

    @Test
    fun `Surrounding whitespace is trimmed`() {
        val result = factory().provideCorsConfig(properties(listOf("  X-Requested-With  ")))

        assertInstanceOf(EnabledCorsConfig::class.java, result)
        assertEquals(listOf("X-Requested-With"), (result as EnabledCorsConfig).allowedHeaders)
    }

    @Test
    fun `Blank header produces a DisabledCorsConfig`() {
        val result = factory().provideCorsConfig(properties(listOf("   ", "X-Requested-With")))

        assertInstanceOf(DisabledCorsConfig::class.java, result)
        assertTrue(errorMessages(result as DisabledCorsConfig).any { it.contains("config.empty") })
    }

    @Test
    fun `Several headers declared in a single entry produce a DisabledCorsConfig`() {
        val result = factory().provideCorsConfig(properties(listOf("X-Foo, X-Bar")))

        assertInstanceOf(DisabledCorsConfig::class.java, result)
        assertTrue(
            errorMessages(result as DisabledCorsConfig).any {
                it.contains("config.cors.allowed_headers.invalid")
            }
        )
    }

    @Test
    fun `Header containing a carriage return produces a DisabledCorsConfig`() {
        val result = factory().provideCorsConfig(properties(listOf("X-Foo\r\nX-Injected: evil")))

        assertInstanceOf(DisabledCorsConfig::class.java, result)
        assertTrue(
            errorMessages(result as DisabledCorsConfig).any {
                it.contains("config.cors.allowed_headers.invalid")
            }
        )
    }

    @Test
    fun `Wildcard produces a DisabledCorsConfig`() {
        val result = factory().provideCorsConfig(properties(listOf("*")))

        assertInstanceOf(DisabledCorsConfig::class.java, result)
        assertTrue(
            errorMessages(result as DisabledCorsConfig).any {
                it.contains("config.cors.allowed_headers.wildcard_unsupported")
            }
        )
    }
}

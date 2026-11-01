package com.sympauthy.config

import com.sympauthy.config.exception.ConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConfigParserTest {

    private val parser = ConfigParser()

    @Test
    fun `getAbsoluteUriOrThrow - Answers an absolute URL`() {
        val uri = parser.getAbsoluteUriOrThrow("https://auth.example/path", "urls.root") { it }

        assertEquals("https://auth.example/path", uri.toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://auth.example/a b", "https://auth.example/a%zz", "/relative", "ftp://auth.example"])
    fun `getAbsoluteUriOrThrow - Refuses what is no absolute URL, naming the key`(value: String) {
        val exception = assertThrows<ConfigurationException> {
            parser.getAbsoluteUriOrThrow(value, "urls.root") { it }
        }

        assertEquals("urls.root", exception.key)
        assertEquals("config.invalid_url", exception.messageId)
    }
}

package com.sympauthy.config

import com.sympauthy.config.exception.ConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConfigTemplateResolverTest {

    private val resolver = ConfigTemplateResolver()

    @Test
    fun `resolve - Replaces single template variable`() {
        val context = mapOf("urls.root" to "https://example.com")
        val result = resolver.resolve("\${urls.root}/callback", context, "test.key")
        assertEquals("https://example.com/callback", result)
    }

    @Test
    fun `resolve - Replaces multiple template variables`() {
        val context = mapOf(
            "urls.root" to "https://example.com",
            "client.uris.app" to "https://app.example.com"
        )
        val result = resolver.resolve("\${urls.root}/api \${client.uris.app}/callback", context, "test.key")
        assertEquals("https://example.com/api https://app.example.com/callback", result)
    }

    @Test
    fun `resolve - Returns string unchanged when no templates present`() {
        val context = mapOf("urls.root" to "https://example.com")
        val result = resolver.resolve("https://example.com/callback", context, "test.key")
        assertEquals("https://example.com/callback", result)
    }

    @Test
    fun `resolve - Throws ConfigurationException for unknown template variable`() {
        val context = mapOf("urls.root" to "https://example.com")
        val exception = assertThrows<ConfigurationException> {
            resolver.resolve("\${unknown.var}/callback", context, "test.key")
        }
        assertEquals("config.unknown_template", exception.messageId)
        assertEquals("unknown.var", exception.values["template"])
    }

    @Test
    fun `resolve - Works with empty context and no templates`() {
        val result = resolver.resolve("https://example.com/callback", emptyMap(), "test.key")
        assertEquals("https://example.com/callback", result)
    }

    @Test
    fun `resolve - Replaces same variable used multiple times`() {
        val context = mapOf("urls.root" to "https://example.com")
        val result = resolver.resolve("\${urls.root}/a \${urls.root}/b", context, "test.key")
        assertEquals("https://example.com/a https://example.com/b", result)
    }
}

package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.ConfigTemplateResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI

class ClientConfigFieldParserTest {

    private val fieldParser = ClientConfigFieldParser(ConfigParser(), ConfigTemplateResolver())

    private val rootUri = URI.create("https://auth.example.com")

    private val uris = mapOf("web" to "https://app.example.com")

    @Test
    fun `parseUri - Resolves the template variables of a client`() {
        val ctx = ConfigParsingContext()

        val result = fieldParser.parseUri(ctx, "clients.app.return-uri", uris, "\${client.uris.web}/account", rootUri)

        assertEquals("https://app.example.com/account", result)
        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `parseUri - Accepts a URL of a scheme of the client's own`() {
        val ctx = ConfigParsingContext()

        val result = fieldParser.parseUri(ctx, "clients.app.return-uri", uris, "myapp://account", rootUri)

        assertEquals("myapp://account", result)
    }

    @Test
    fun `parseUri - Refuses a URL that is not absolute against its key`() {
        val ctx = ConfigParsingContext()

        val result = fieldParser.parseUri(ctx, "clients.app.return-uri", uris, "not-a-url", rootUri)

        assertNull(result)
        val error = ctx.errors.single()
        assertEquals("clients.app.return-uri", error.key)
        assertEquals("config.invalid_url", error.messageId)
    }

    @Test
    fun `parseUri - Answers null when the key is not written`() {
        val ctx = ConfigParsingContext()

        assertNull(fieldParser.parseUri(ctx, "clients.app.return-uri", uris, null, rootUri))
        assertFalse(ctx.hasErrors)
    }

    @Test
    fun `parseUris - Refuses each URL that is not absolute against its index`() {
        val ctx = ConfigParsingContext()

        val result = fieldParser.parseUris(
            ctx, "clients.app.allowed-return-uris", uris, listOf("\${urls.root}/account", "not-a-url"), rootUri
        )

        assertNull(result)
        assertEquals("clients.app.allowed-return-uris[1]", ctx.errors.single().key)
    }
}

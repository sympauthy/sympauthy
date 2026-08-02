package com.sympauthy.business.manager.client

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.client.Client
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

class ClientRedirectUriManagerTest {

    private val manager = ClientRedirectUriManager()

    // --- parseRequestedRedirectUri tests ---

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is null`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, null, recoverable = true)
        }
        assertEquals("client.redirect_uri.missing", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is blank`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "   ", recoverable = true)
        }
        assertEquals("client.redirect_uri.missing", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is not a valid URI`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "://not-valid", recoverable = true)
        }
        assertEquals("client.redirect_uri.invalid", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is not in allowedRedirectUris`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://allowed.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://other.com/callback", recoverable = true)
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Accepts redirect_uri matching an allowed URI exactly`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val result = manager.parseRequestedRedirectUri(client, "https://example.com/callback", recoverable = true)
        assertEquals(URI("https://example.com/callback"), result)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different path`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/other-path", recoverable = true)
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with extra query params`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/callback?extra=param", recoverable = true)
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different case in scheme`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "HTTPS://example.com/callback", recoverable = true)
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different case in host`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://Example.com/callback", recoverable = true)
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    // --- recoverable flag ---

    @Test
    fun `parseRequestedRedirectUri - Surfaces a recoverable error when recoverable is true`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://allowed.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://other.com/callback", recoverable = true)
        }
        assertTrue(exception.recoverable, "a caller-supplied bad URI should be a recoverable (400) error")
    }

    @Test
    fun `parseRequestedRedirectUri - Surfaces a non-recoverable error when recoverable is false`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://allowed.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://other.com/callback", recoverable = false)
        }
        assertFalse(exception.recoverable, "the authorize flow keeps a non-recoverable (500) error")
    }

    // --- matchesAllowedRedirectUri loopback tests ---

    @Test
    fun `matchesAllowedRedirectUri - Allows different port on 127_0_0_1 loopback`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://127.0.0.1:12345/callback",
            listOf("http://127.0.0.1:8080/callback")
        )
        assertTrue(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Allows different port on IPv6 loopback`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://[::1]:12345/callback",
            listOf("http://[::1]:8080/callback")
        )
        assertTrue(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Rejects different port on localhost`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://localhost:12345/callback",
            listOf("http://localhost:8080/callback")
        )
        assertFalse(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Rejects loopback with different path`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://127.0.0.1:12345/other",
            listOf("http://127.0.0.1:8080/callback")
        )
        assertFalse(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Rejects cross-family loopback mismatch`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://127.0.0.1:12345/callback",
            listOf("http://[::1]:8080/callback")
        )
        assertFalse(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Rejects loopback flexibility for custom scheme`() {
        val result = manager.matchesAllowedRedirectUri(
            "myapp://127.0.0.1:12345/callback",
            listOf("myapp://127.0.0.1:8080/callback")
        )
        assertFalse(result)
    }
}

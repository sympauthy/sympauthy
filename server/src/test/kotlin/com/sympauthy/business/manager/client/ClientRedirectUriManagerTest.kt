package com.sympauthy.business.manager.client

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.client.Client
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(MockKExtension::class)
class ClientRedirectUriManagerTest {

    private val manager = ClientRedirectUriManager()

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is null`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, null)
        }
        assertEquals("client.redirect_uri.missing", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is blank`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "   ")
        }
        assertEquals("client.redirect_uri.missing", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is not a valid URI`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "://not-valid")
        }
        assertEquals("client.redirect_uri.invalid", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is not in allowedRedirectUris`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://allowed.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://other.com/callback")
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Accepts redirect_uri matching an allowed URI exactly`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val result = manager.parseRequestedRedirectUri(client, "https://example.com/callback")
        assertEquals(URI("https://example.com/callback"), result)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different path`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/other-path")
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with extra query params`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/callback?extra=param")
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different case in scheme`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "HTTPS://example.com/callback")
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different case in host`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://Example.com/callback")
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Surfaces a non-recoverable error`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://allowed.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://other.com/callback")
        }
        assertFalse(
            exception.recoverable,
            "the authorize flow keeps a non-recoverable error, which fails the session rather " +
                    "than answering the caller"
        )
    }

    @Test
    fun `parseRequestedRedirectUri - Refuses a URI declared only as a return URI`() {
        val client = returnClient(allowedRedirectUris = listOf("https://example.com/callback"))
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/account")
        }
        assertEquals("client.redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedReturnUri - Answers the client return URI when the request names none`() {
        val client = returnClient()

        assertEquals(URI("https://example.com/account"), manager.parseRequestedReturnUri(client, null))
        assertEquals(URI("https://example.com/account"), manager.parseRequestedReturnUri(client, "  "))
    }

    @Test
    fun `parseRequestedReturnUri - Accepts the client return URI and its allowed return URIs`() {
        val client = returnClient(allowedReturnUris = listOf("myapp://account"))

        assertEquals(
            URI("https://example.com/account"),
            manager.parseRequestedReturnUri(client, "https://example.com/account")
        )
        assertEquals(URI("myapp://account"), manager.parseRequestedReturnUri(client, "myapp://account"))
    }

    @Test
    fun `parseRequestedReturnUri - Accepts an allowed return URI when the client declares no return URI`() {
        val client = returnClient(returnUri = null, allowedReturnUris = listOf("myapp://account"))

        assertEquals(URI("myapp://account"), manager.parseRequestedReturnUri(client, "myapp://account"))
    }

    @Test
    fun `parseRequestedReturnUri - Refuses with missing when neither the request nor the client names one`() {
        val client = returnClient(returnUri = null)
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedReturnUri(client, null)
        }
        assertEquals("client.return_uri.missing", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `parseRequestedReturnUri - Refuses with invalid a URI that cannot be read`() {
        val client = returnClient()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedReturnUri(client, "://not-valid")
        }
        assertEquals("client.return_uri.invalid", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `parseRequestedReturnUri - Refuses a URI declared only as a redirect URI`() {
        val client = returnClient(allowedRedirectUris = listOf("https://example.com/callback"))
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedReturnUri(client, "https://example.com/callback")
        }
        assertEquals("client.return_uri.not_allowed", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    private fun returnClient(
        returnUri: String? = "https://example.com/account",
        allowedReturnUris: List<String> = emptyList(),
        allowedRedirectUris: List<String> = emptyList()
    ) = mockk<Client> {
        every { id } returns "app"
        every { this@mockk.returnUri } returns returnUri
        every { this@mockk.allowedReturnUris } returns allowedReturnUris
        every { this@mockk.allowedRedirectUris } returns allowedRedirectUris
    }

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

package com.sympauthy.api.controller.oauth2.util

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.client.Client
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class ClientAuthenticationUtilTest {

    @MockK
    lateinit var clientManager: ClientManager

    @InjectMockKs
    lateinit var util: ClientAuthenticationUtil

    private fun mockRequestWithoutAuth(): HttpRequest<*> {
        val headers = mockk<HttpHeaders> {
            every { authorization } returns Optional.empty()
        }
        return mockk {
            every { this@mockk.headers } returns headers
        }
    }

    private fun mockRequestWithBasicAuth(username: String, password: String): HttpRequest<*> {
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        val headers = mockk<HttpHeaders> {
            every { authorization } returns Optional.of("Basic $encoded")
        }
        return mockk {
            every { this@mockk.headers } returns headers
        }
    }

    // --- resolveClient tests ---

    @Test
    fun `resolveClient - Authenticates with form params`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClient("client1", "secret1") } returns client

        val result = util.resolveClient(mockRequestWithoutAuth(), "client1", "secret1")

        assertEquals(client, result)
    }

    @Test
    fun `resolveClient - Throws when no credentials provided`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            util.resolveClient(mockRequestWithoutAuth(), null, null)
        }
        assertEquals("authentication.missing_credentials", exception.detailsId)
    }

    @Test
    fun `resolveClient - Throws when credentials are wrong`() = runTest {
        coEvery { clientManager.authenticateClient("client1", "wrong") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClient(mockRequestWithoutAuth(), "client1", "wrong")
        }
        assertEquals("authentication.wrong", exception.detailsId)
    }

    // --- resolveClientForAuthorizationCodeGrant tests ---

    @Test
    fun `resolveClientForAuthorizationCodeGrant - Authenticates confidential client`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClient("client1", "secret1") } returns client

        val result = util.resolveClientForAuthorizationCodeGrant(
            mockRequestWithoutAuth(), "client1", "secret1"
        )

        assertEquals(client, result)
    }

    @Test
    fun `resolveClientForAuthorizationCodeGrant - Resolves public client without secret`() = runTest {
        val publicClient = mockk<Client> { every { `public` } returns true }
        coEvery { clientManager.findPublicClientByIdOrNull("public-app") } returns publicClient

        val result = util.resolveClientForAuthorizationCodeGrant(
            mockRequestWithoutAuth(), "public-app", null
        )

        assertEquals(publicClient, result)
    }

    @Test
    fun `resolveClientForAuthorizationCodeGrant - Rejects unknown client without secret`() = runTest {
        coEvery { clientManager.findPublicClientByIdOrNull("unknown") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClientForAuthorizationCodeGrant(
                mockRequestWithoutAuth(), "unknown", null
            )
        }
        assertEquals("authentication.wrong", exception.detailsId)
    }

    @Test
    fun `resolveClientForAuthorizationCodeGrant - Rejects confidential client without secret`() = runTest {
        coEvery { clientManager.findPublicClientByIdOrNull("confidential") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClientForAuthorizationCodeGrant(
                mockRequestWithoutAuth(), "confidential", ""
            )
        }
        assertEquals("authentication.wrong", exception.detailsId)
    }

    @Test
    fun `resolveClientForAuthorizationCodeGrant - Uses Basic Auth when present`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClient("client1", "secret1") } returns client

        val result = util.resolveClientForAuthorizationCodeGrant(
            mockRequestWithBasicAuth("client1", "secret1"), null, null
        )

        assertEquals(client, result)
    }

    @Test
    fun `resolveClientForAuthorizationCodeGrant - Throws when no credentials provided`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            util.resolveClientForAuthorizationCodeGrant(
                mockRequestWithoutAuth(), null, null
            )
        }
        assertEquals("authentication.missing_credentials", exception.detailsId)
    }
}

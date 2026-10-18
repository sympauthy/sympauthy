package com.sympauthy.api.controller.oauth2.util

import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil.Companion.BASIC_CHALLENGE
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_CLIENT
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus.BAD_REQUEST
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun mockRequestWithoutAuth(): HttpRequest<*> = mockRequestWithAuthorization(null)

    private fun mockRequestWithBasicAuth(username: String, password: String, scheme: String = "Basic"): HttpRequest<*> {
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        return mockRequestWithAuthorization("$scheme $encoded")
    }

    private fun mockRequestWithAuthorization(authorization: String?): HttpRequest<*> {
        val headers = mockk<HttpHeaders> {
            every { this@mockk.authorization } returns Optional.ofNullable(authorization)
        }
        return mockk {
            every { this@mockk.headers } returns headers
        }
    }

    private fun assertChallenged(exception: OAuth2Exception) {
        assertEquals(INVALID_CLIENT, exception.errorCode)
        assertEquals(UNAUTHORIZED, exception.status)
        assertEquals(BASIC_CHALLENGE, exception.challenge)
    }

    private fun assertNotChallenged(exception: OAuth2Exception) {
        assertEquals(INVALID_CLIENT, exception.errorCode)
        assertEquals(BAD_REQUEST, exception.status)
        assertNull(exception.challenge)
    }

    @Test
    fun `resolveClient - Authenticates with form params`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClientOrNull("client1", "secret1") } returns client

        val result = util.resolveClient(mockRequestWithoutAuth(), "client1", "secret1")

        assertEquals(client, result)
    }

    @Test
    fun `resolveClient - Throws with a challenge when no credentials provided`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            util.resolveClient(mockRequestWithoutAuth(), null, null)
        }
        assertChallenged(exception)
        assertEquals("authentication.missing_credentials", exception.detailsId)
    }

    @Test
    fun `resolveClient - Throws with a challenge when credentials in the body are wrong`() = runTest {
        coEvery { clientManager.authenticateClientOrNull("client1", "wrong") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClient(mockRequestWithoutAuth(), "client1", "wrong")
        }
        assertChallenged(exception)
        assertEquals("authentication.wrong", exception.detailsId)
    }

    @Test
    fun `resolveClient - Throws with a challenge when Basic Auth credentials are wrong`() = runTest {
        coEvery { clientManager.authenticateClientOrNull("client1", "wrong") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClient(mockRequestWithBasicAuth("client1", "wrong"), null, null)
        }
        assertChallenged(exception)
        assertEquals("authentication.wrong", exception.detailsId)
    }

    @Test
    fun `resolveClient - Authenticates with a Basic scheme spelled in another case`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClientOrNull("client1", "secret1") } returns client

        val result = util.resolveClient(mockRequestWithBasicAuth("client1", "secret1", scheme = "basic"), null, null)

        assertEquals(client, result)
    }

    @Test
    fun `resolveClient - Authenticates with more than one space after the Basic scheme`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClientOrNull("client1", "secret1") } returns client

        val result = util.resolveClient(mockRequestWithBasicAuth("client1", "secret1", scheme = "Basic  "), null, null)

        assertEquals(client, result)
    }

    @Test
    fun `resolveClient - Authenticates with Basic Auth credentials form-encoded`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClientOrNull("client:1", "a+b/c= é") } returns client

        val result = util.resolveClient(mockRequestWithBasicAuth("client%3A1", "a%2Bb%2Fc%3D+%C3%A9"), null, null)

        assertEquals(client, result)
    }

    @Test
    fun `resolveClient - Throws with a challenge when Basic Auth credentials hold an escape that does not decode`() =
        runTest {
            val exception = assertThrows<OAuth2Exception> {
                util.resolveClient(mockRequestWithBasicAuth("client1", "100%"), null, null)
            }
            assertChallenged(exception)
            assertEquals("authentication.wrong", exception.detailsId)
        }

    @Test
    fun `resolveClientAllowingPublic - Authenticates confidential client`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClientOrNull("client1", "secret1") } returns client

        val result = util.resolveClientAllowingPublic(
            mockRequestWithoutAuth(), "client1", "secret1"
        )

        assertEquals(client, result)
    }

    @Test
    fun `resolveClientAllowingPublic - Resolves public client without secret`() = runTest {
        val publicClient = mockk<Client>()
        coEvery { clientManager.findPublicClientByIdOrNull("public-app") } returns publicClient

        val result = util.resolveClientAllowingPublic(
            mockRequestWithoutAuth(), "public-app", null
        )

        assertEquals(publicClient, result)
    }

    @Test
    fun `resolveClientAllowingPublic - Reads the body when the header uses another scheme`() = runTest {
        val publicClient = mockk<Client>()
        coEvery { clientManager.findPublicClientByIdOrNull("public-app") } returns publicClient

        val result = util.resolveClientAllowingPublic(
            mockRequestWithAuthorization("Bearer some-access-token"), "public-app", null
        )

        assertEquals(publicClient, result)
    }

    @Test
    fun `resolveClientAllowingPublic - Rejects unknown client without secret, without a challenge`() = runTest {
        coEvery { clientManager.findPublicClientByIdOrNull("unknown") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClientAllowingPublic(
                mockRequestWithoutAuth(), "unknown", null
            )
        }
        assertNotChallenged(exception)
        assertEquals("authentication.wrong", exception.detailsId)
    }

    @Test
    fun `resolveClientAllowingPublic - Rejects confidential client without secret, without a challenge`() = runTest {
        coEvery { clientManager.findPublicClientByIdOrNull("confidential") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClientAllowingPublic(
                mockRequestWithoutAuth(), "confidential", ""
            )
        }
        assertNotChallenged(exception)
        assertEquals("authentication.wrong", exception.detailsId)
    }

    @Test
    fun `resolveClientAllowingPublic - Rejects wrong credentials in the body, without a challenge`() = runTest {
        coEvery { clientManager.authenticateClientOrNull("client1", "wrong") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClientAllowingPublic(
                mockRequestWithoutAuth(), "client1", "wrong"
            )
        }
        assertNotChallenged(exception)
        assertEquals("authentication.wrong", exception.detailsId)
    }

    @Test
    fun `resolveClientAllowingPublic - Uses Basic Auth when present`() = runTest {
        val client = mockk<Client>()
        coEvery { clientManager.authenticateClientOrNull("client1", "secret1") } returns client

        val result = util.resolveClientAllowingPublic(
            mockRequestWithBasicAuth("client1", "secret1"), null, null
        )

        assertEquals(client, result)
    }

    @Test
    fun `resolveClientAllowingPublic - Throws with a challenge when Basic Auth credentials are wrong`() = runTest {
        coEvery { clientManager.authenticateClientOrNull("client1", "wrong") } returns null

        val exception = assertThrows<OAuth2Exception> {
            util.resolveClientAllowingPublic(
                mockRequestWithBasicAuth("client1", "wrong"), "public-app", null
            )
        }
        assertChallenged(exception)
    }

    @Test
    fun `resolveClientAllowingPublic - Throws with a challenge when Basic Auth credentials do not decode`() =
        runTest {
            // No stub answers for the client the body names: the malformed header is refused before the body is read.
            val exception = assertThrows<OAuth2Exception> {
                util.resolveClientAllowingPublic(
                    mockRequestWithAuthorization("Basic not-base64!"), "public-app", null
                )
            }
            assertChallenged(exception)
            assertEquals("authentication.wrong", exception.detailsId)
        }

    @Test
    fun `resolveClientAllowingPublic - Throws without a challenge when no credentials provided`() = runTest {
        val exception = assertThrows<OAuth2Exception> {
            util.resolveClientAllowingPublic(
                mockRequestWithoutAuth(), null, null
            )
        }
        assertNotChallenged(exception)
        assertEquals("authentication.missing_credentials", exception.detailsId)
    }
}

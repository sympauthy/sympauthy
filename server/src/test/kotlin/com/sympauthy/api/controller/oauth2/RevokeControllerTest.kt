package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.just
import io.mockk.runs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RevokeControllerTest {

    @MockK
    lateinit var tokenManager: TokenManager

    @MockK
    lateinit var clientAuthenticationUtil: ClientAuthenticationUtil

    @InjectMockKs
    lateinit var controller: RevokeController

    @Test
    fun `revokeToken - Throws invalid_request when token parameter is missing`() = runTest {
        val request = mockk<HttpRequest<*>>()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, "test-client", "secret")
        } returns mockk<Client>()

        val exception = assertThrows<OAuth2Exception> {
            controller.revokeToken(
                request = request,
                token = null,
                clientId = "test-client",
                clientSecret = "secret"
            )
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        coVerify(exactly = 0) { tokenManager.revokeTokenByEncodedToken(any(), any()) }
    }

    @Test
    fun `revokeToken - Revokes the token of a public client sending its client_id alone`() = runTest {
        val request = mockk<HttpRequest<*>>()
        val publicClient = mockk<Client>()
        coEvery {
            clientAuthenticationUtil.resolveClientAllowingPublic(request, "public-app", null)
        } returns publicClient
        coEvery { tokenManager.revokeTokenByEncodedToken(publicClient, "some-token") } just runs

        val response = controller.revokeToken(
            request = request,
            token = "some-token",
            clientId = "public-app",
            clientSecret = null
        )

        assertEquals(HttpStatus.OK, response.status)
        coVerify(exactly = 0) { clientAuthenticationUtil.resolveClient(any(), any(), any()) }
    }
}

package com.sympauthy.api.controller.oauth2

import com.sympauthy.api.controller.oauth2.util.ClientAuthenticationUtil
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import io.micronaut.http.HttpRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
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
        coEvery { clientAuthenticationUtil.resolveClient(request, "test-client", "secret") } returns mockk<Client>()

        val exception = assertThrows<OAuth2Exception> {
            controller.revokeToken(
                request = request,
                token = null,
                tokenTypeHint = null,
                clientId = "test-client",
                clientSecret = "secret"
            )
        }

        assertEquals(INVALID_REQUEST, exception.errorCode)
        assertEquals("token.missing_param", exception.detailsId)
        coVerify(exactly = 0) { tokenManager.revokeTokenByEncodedToken(any(), any(), any()) }
    }
}

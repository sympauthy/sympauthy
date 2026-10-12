package com.sympauthy.security

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.AuthenticationToken
import io.micronaut.http.HttpStatus.UNAUTHORIZED
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class AccessTokenValidatorTest {

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var tokenManager: TokenManager

    @MockK
    lateinit var jwtManager: JwtManager

    private fun validator() = AccessTokenValidator<Any>(clientManager, scopeManager, tokenManager, jwtManager)

    private fun stubToken(token: AuthenticationToken) {
        val decodedToken = mockk<DecodedJwt>()
        coEvery { jwtManager.decodeAndVerify(ACCESS_KEY, ENCODED_TOKEN) } returns decodedToken
        coEvery { tokenManager.getAuthenticationToken(decodedToken) } returns token
    }

    @Test
    fun `validateToken - Refuse with 401 a token issued to a client the configuration no longer declares`() =
        runTest {
            stubToken(mockk { every { clientId } returns CLIENT_ID })
            coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns null

            val exception = assertThrows<LocalizedHttpException> {
                validator().validateToken(ENCODED_TOKEN, null).asFlow().single()
            }

            assertEquals(UNAUTHORIZED, exception.status)
            assertEquals("token.client_disabled", exception.detailsId)
            assertEquals(CLIENT_ID, exception.values["clientId"])
        }

    @Test
    fun `validateToken - Carry the client a client_credentials token was issued to`() = runTest {
        val client = mockk<Client>()
        stubToken(mockk {
            every { clientId } returns CLIENT_ID
            every { userId } returns null
            every { clientScopes } returns emptyList()
        })
        coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns client

        val authentication = validator().validateToken(ENCODED_TOKEN, null).asFlow().single()

        assertSame(client, (authentication as ClientAuthentication).client)
    }

    @Test
    fun `validateToken - Carry the client a token issued for an end-user was issued to`() = runTest {
        val client = mockk<Client>()
        stubToken(mockk {
            every { clientId } returns CLIENT_ID
            every { userId } returns UUID.randomUUID()
            every { consentedScopes } returns emptyList()
            every { grantedScopes } returns emptyList()
        })
        coEvery { clientManager.findClientByIdOrNull(CLIENT_ID) } returns client

        val authentication = validator().validateToken(ENCODED_TOKEN, null).asFlow().single()

        assertSame(client, (authentication as UserAuthentication).client)
    }

    private companion object {
        const val ENCODED_TOKEN = "encoded-access-token"
        const val CLIENT_ID = "client-app"
    }
}

package com.sympauthy.business.model.token

import com.sympauthy.business.model.client.GrantType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class TokenRequestTest {

    @Test
    fun `toString - Leaves out the credentials a request carries`() {
        val requests = listOf(
            TokenRequest.AuthorizationCode(code = "secret-code", codeVerifier = "secret-verifier"),
            TokenRequest.RefreshToken(refreshToken = "secret-refresh"),
            TokenRequest.TokenExchange(subjectToken = "secret-subject", requestedSubject = "user", audience = null)
        )

        requests.forEach { request ->
            assertFalse(request.toString().contains("secret"), "$request")
        }
    }

    @Test
    fun `toString - Leaves out the token an issued token carries`() {
        val token = EncodedAuthenticationToken(
            id = UUID.randomUUID(),
            type = AuthenticationTokenType.ACCESS,
            token = "secret-jwt",
            scopes = listOf("openid"),
            issueDate = LocalDateTime.now(),
            expirationDate = null
        )

        assertFalse(token.toString().contains("secret"))
    }

    @Test
    fun `grantType - Names the grant each request is read for`() {
        assertEquals(GrantType.AUTHORIZATION_CODE, TokenRequest.AuthorizationCode("code", null).grantType)
        assertEquals(GrantType.REFRESH_TOKEN, TokenRequest.RefreshToken("token").grantType)
        assertEquals(GrantType.CLIENT_CREDENTIALS, TokenRequest.ClientCredentials(null).grantType)
        assertEquals(GrantType.TOKEN_EXCHANGE, TokenRequest.TokenExchange("token", "user", null).grantType)
    }
}

package com.sympauthy.api.mapper

import com.sympauthy.business.model.token.AuthenticationTokenType
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.IssuedTokens
import com.sympauthy.business.model.token.TokenIssuance
import com.sympauthy.business.model.token.TokenRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class TokenResourceMapperTest {

    private val mapper = TokenResourceMapper()

    private fun token(
        token: String,
        type: AuthenticationTokenType = AuthenticationTokenType.ACCESS,
        scopes: List<String> = emptyList(),
        expirationDate: LocalDateTime? = null
    ) = EncodedAuthenticationToken(
        id = UUID.randomUUID(),
        type = type,
        token = token,
        scopes = scopes,
        issueDate = ISSUE_DATE,
        expirationDate = expirationDate
    )

    @Test
    fun `toResource - Answers every token the code exchange issued`() {
        val issuance = TokenIssuance(
            request = TokenRequest.AuthorizationCode("the-code", "the-verifier"),
            tokens = IssuedTokens(
                accessToken = token("access-jwt", scopes = listOf("openid", "profile")),
                refreshToken = token("refresh-jwt", type = AuthenticationTokenType.REFRESH),
                idToken = token("id-jwt", type = AuthenticationTokenType.ID)
            )
        )

        val resource = mapper.toResource(issuance, dpopJkt = null)

        assertEquals("access-jwt", resource.accessToken)
        assertEquals("bearer", resource.tokenType)
        assertEquals("openid profile", resource.scope)
        assertEquals("refresh-jwt", resource.refreshToken)
        assertEquals("id-jwt", resource.idToken)
        assertNull(resource.issuedTokenType)
    }

    @Test
    fun `toResource - Answers a refresh issuing no refresh token with the one presented`() {
        val issuance = TokenIssuance(
            request = TokenRequest.RefreshToken("old-refresh"),
            tokens = IssuedTokens(accessToken = token("new-access"))
        )

        val resource = mapper.toResource(issuance, dpopJkt = null)

        assertEquals("old-refresh", resource.refreshToken)
        assertNull(resource.idToken)
    }

    @Test
    fun `toResource - Answers a refresh with the refresh token it issued`() {
        val issuance = TokenIssuance(
            request = TokenRequest.RefreshToken("old-refresh"),
            tokens = IssuedTokens(
                accessToken = token("new-access"),
                refreshToken = token("new-refresh", type = AuthenticationTokenType.REFRESH)
            )
        )

        assertEquals("new-refresh", mapper.toResource(issuance, dpopJkt = null).refreshToken)
    }

    @Test
    fun `toResource - Answers a token exchange with the type of the token it issued`() {
        val issuance = TokenIssuance(
            request = TokenRequest.TokenExchange("a-token", "a-subject", audience = null),
            tokens = IssuedTokens(accessToken = token("act-as"))
        )

        val resource = mapper.toResource(issuance, dpopJkt = null)

        assertEquals(TokenRequest.TokenExchange.ACCESS_TOKEN_TYPE, resource.issuedTokenType)
        assertNull(resource.refreshToken)
    }

    @Test
    fun `toResource - Answers a token bound to a key as a DPoP token`() {
        val issuance = TokenIssuance(
            request = TokenRequest.ClientCredentials(scope = null),
            tokens = IssuedTokens(accessToken = token("cc-access"))
        )

        val resource = mapper.toResource(issuance, dpopJkt = "a-jkt")

        assertEquals("DPoP", resource.tokenType)
        assertNull(resource.scope)
        assertNull(resource.expiresIn, "a token without an expiration date has no lifetime to report")
    }

    @Test
    fun `toResource - Answers the lifetime of the access token in seconds from its issue`() {
        val issuance = TokenIssuance(
            request = TokenRequest.ClientCredentials(scope = null),
            tokens = IssuedTokens(accessToken = token("cc-access", expirationDate = ISSUE_DATE.plusHours(1)))
        )

        assertEquals(3600, mapper.toResource(issuance, dpopJkt = null).expiresIn)
    }

    private companion object {
        val ISSUE_DATE: LocalDateTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
    }
}

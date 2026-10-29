package com.sympauthy.api.mapper

import com.sympauthy.api.resource.oauth2.TokenResource
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.TokenIssuance
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.util.nullIfBlank
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class TokenResourceMapper {

    /**
     * The token response answering the request of [issuance] with the tokens issued for it, as a `DPoP` token
     * where [dpopJkt] names the key they are bound to and as a bearer token otherwise.
     *
     * A refresh issuing no new refresh token answers with the one presented, which the client keeps using, and
     * only a token exchange names the type of the token it issued.
     */
    fun toResource(
        issuance: TokenIssuance,
        dpopJkt: String?
    ): TokenResource {
        val request = issuance.request
        val tokens = issuance.tokens
        return TokenResource(
            accessToken = tokens.accessToken.token,
            tokenType = if (dpopJkt != null) TOKEN_TYPE_DPOP else TOKEN_TYPE_BEARER,
            expiresIn = getExpiresIn(tokens.accessToken),
            scope = tokens.accessToken.scopes.joinToString(" ").nullIfBlank(),
            refreshToken = tokens.refreshToken?.token ?: (request as? TokenRequest.RefreshToken)?.refreshToken,
            idToken = tokens.idToken?.token,
            issuedTokenType = if (request is TokenRequest.TokenExchange) {
                TokenRequest.TokenExchange.ACCESS_TOKEN_TYPE
            } else null
        )
    }

    /**
     * Returns the lifetime in seconds of the [accessToken], or null if it never expires.
     *
     * The lifetime is measured from the issue date of the token rather than from now, so that the
     * returned value is exactly the configured lifetime instead of being shortened by the time spent
     * handling the request.
     */
    private fun getExpiresIn(accessToken: EncodedAuthenticationToken): Int? {
        return accessToken.expirationDate?.let {
            Duration.between(accessToken.issueDate, it).toSeconds().toInt()
        }
    }

    private companion object {
        const val TOKEN_TYPE_BEARER = "bearer"
        const val TOKEN_TYPE_DPOP = "DPoP"
    }
}

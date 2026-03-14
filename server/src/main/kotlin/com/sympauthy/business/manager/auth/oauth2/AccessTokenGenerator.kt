package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.ACCESS
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Singleton
class AccessTokenGenerator(
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: EncodedAuthenticationTokenMapper,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Generate a new access token using the information stored in a [authorizeAttempt].
     */
    suspend fun generateAccessToken(
        authorizeAttempt: CompletedAuthorizeAttempt,
        userId: UUID
    ) = generateAccessToken(
        userId = userId,
        clientId = authorizeAttempt.clientId,
        scopes = authorizeAttempt.grantedScopes,
        authorizeAttemptId = authorizeAttempt.id,
        grantType = "authorization_code"
    )

    /**
     * Generate a new access token using the information stored in a [refreshToken].
     */
    suspend fun generateAccessToken(
        refreshToken: AuthenticationToken
    ) = generateAccessToken(
        userId = refreshToken.userId,
        clientId = refreshToken.clientId,
        scopes = refreshToken.scopes,
        authorizeAttemptId = refreshToken.authorizeAttemptId,
        grantType = "refresh_token"
    )

    /**
     * Generate an access token for client credentials flow (machine-to-machine).
     * This token is not associated with any end-user.
     */
    suspend fun generateAccessTokenForClient(
        clientId: String,
        scopes: List<String>
    ): EncodedAuthenticationToken {
        return generateAccessToken(
            userId = null,
            clientId = clientId,
            scopes = scopes,
            authorizeAttemptId = null,
            grantType = "client_credentials"
        )
    }

    internal suspend fun generateAccessToken(
        userId: UUID?,
        clientId: String,
        scopes: List<String>,
        authorizeAttemptId: UUID?,
        grantType: String
    ): EncodedAuthenticationToken {
        val authConfig = uncheckedAuthConfig.orThrow()

        val issueDate = LocalDateTime.now()
        val expirationDate = issueDate.plus(authConfig.token.accessExpiration)
        val entity = AuthenticationTokenEntity(
            userId = userId,
            type = ACCESS.name,
            clientId = clientId,
            scopes = scopes.toTypedArray(),
            authorizeAttemptId = authorizeAttemptId,
            grantType = grantType,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(
            name = JwtManager.ACCESS_KEY,
            headers = mapOf("typ" to "at+jwt")
        ) {
            entity.id?.toString()?.let(this::withJWTId)
            withAudience(authConfig.audience)
            withSubject(userId?.toString() ?: clientId)
            withClaim("client_id", clientId)
            withClaim("scope", scopes.joinToString(" "))
            withIssuedAt(issueDate.toInstant(ZoneOffset.UTC))
            withExpiresAt(expirationDate.toInstant(ZoneOffset.UTC))
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }
}

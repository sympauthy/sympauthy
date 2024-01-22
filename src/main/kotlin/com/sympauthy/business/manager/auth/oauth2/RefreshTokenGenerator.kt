package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
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
class RefreshTokenGenerator(
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: EncodedAuthenticationTokenMapper,
    @Inject private val authConfig: AuthConfig
) {

    /**
     * Generate a new refresh token using the information stored in a [authorizeAttempt].
     * Or return null if the refresh token is disabled by the [authConfig].
     */
    suspend fun generateRefreshToken(
        authorizeAttempt: AuthorizeAttempt,
        userId: UUID
    ) = generateRefreshToken(
        userId = userId,
        clientId = authorizeAttempt.clientId,
        scopeTokens = authorizeAttempt.scopeTokens,
        authorizeAttemptId = authorizeAttempt.id
    )

    /**
     * Generate a new refresh token using the information stored in the previous [refreshToken].
     */
    suspend fun generateRefreshToken(
        refreshToken: AuthenticationToken
    ) = generateRefreshToken(
        userId = refreshToken.userId,
        clientId = refreshToken.clientId,
        scopeTokens = refreshToken.scopeTokens,
        authorizeAttemptId = refreshToken.authorizeAttemptId
    )

    internal suspend fun generateRefreshToken(
        userId: UUID,
        clientId: String,
        scopeTokens: List<String>,
        authorizeAttemptId: UUID
    ): EncodedAuthenticationToken? {
        val enabledAuthConfig = authConfig.orThrow()
        if (!enabledAuthConfig.token.refreshEnabled) {
            return null
        }

        val issueDate = LocalDateTime.now()
        val expirationDate = enabledAuthConfig.token.refreshExpiration?.let(issueDate::plus)
        val entity = AuthenticationTokenEntity(
            userId = userId,
            type = REFRESH.name,
            clientId = clientId,
            scopeTokens = scopeTokens.toTypedArray(),
            authorizeAttemptId = authorizeAttemptId,
            revoked = false,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(JwtManager.REFRESH_KEY) {
            entity.id?.toString()?.let(this::withJWTId)
            enabledAuthConfig.audience?.let { this.withAudience(it) }
            withSubject(userId.toString())
            withIssuedAt(issueDate.toInstant(ZoneOffset.UTC))
            expirationDate?.toInstant(ZoneOffset.UTC)?.let(this::withExpiresAt)
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }
}

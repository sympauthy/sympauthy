package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
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
        authorizeAttempt: CompletedAuthorizeAttempt,
        userId: UUID
    ) = generateRefreshToken(
        userId = userId,
        clientId = authorizeAttempt.clientId,
        grantedScopes = authorizeAttempt.grantedScopes,
        grantedAt = authorizeAttempt.grantedAt,
        grantedBy = authorizeAttempt.grantedBy.name,
        consentedScopes = authorizeAttempt.consentedScopes,
        consentedAt = authorizeAttempt.consentedAt,
        consentedBy = authorizeAttempt.consentedBy.name,
        clientScopes = emptyList(),
        authorizeAttemptId = authorizeAttempt.id,
        grantType = "authorization_code"
    )

    /**
     * Generate a new refresh token using the information stored in the previous [refreshToken].
     */
    suspend fun generateRefreshToken(
        refreshToken: AuthenticationToken
    ) = generateRefreshToken(
        userId = refreshToken.userId,
        clientId = refreshToken.clientId,
        grantedScopes = refreshToken.grantedScopes,
        grantedAt = refreshToken.grantedAt,
        grantedBy = refreshToken.grantedBy?.name,
        consentedScopes = refreshToken.consentedScopes,
        consentedAt = refreshToken.consentedAt,
        consentedBy = refreshToken.consentedBy?.name,
        clientScopes = refreshToken.clientScopes,
        authorizeAttemptId = refreshToken.authorizeAttemptId,
        grantType = "refresh_token"
    )

    internal suspend fun generateRefreshToken(
        userId: UUID?,
        clientId: String,
        grantedScopes: List<String>,
        grantedAt: java.time.LocalDateTime?,
        grantedBy: String?,
        consentedScopes: List<String>,
        consentedAt: java.time.LocalDateTime?,
        consentedBy: String?,
        clientScopes: List<String>,
        authorizeAttemptId: UUID?,
        grantType: String
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
            grantedScopes = grantedScopes.toTypedArray(),
            grantedAt = grantedAt,
            grantedBy = grantedBy,
            consentedScopes = consentedScopes.toTypedArray(),
            consentedAt = consentedAt,
            consentedBy = consentedBy,
            clientScopes = clientScopes.toTypedArray(),
            authorizeAttemptId = authorizeAttemptId,
            grantType = grantType,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(JwtManager.REFRESH_KEY) {
            entity.id?.toString()?.let(this::withJWTId)
            withAudience(enabledAuthConfig.audience)
            withSubject(userId?.toString() ?: clientId)
            withIssuedAt(issueDate.toInstant(ZoneOffset.UTC))
            expirationDate?.toInstant(ZoneOffset.UTC)?.let(this::withExpiresAt)
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }
}

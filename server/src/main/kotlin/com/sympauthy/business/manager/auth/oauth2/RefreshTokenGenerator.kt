package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
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
     * Generate a new refresh token using the information stored in the session's [oauth2] request record.
     * Or return null if the refresh token is disabled by the [authConfig].
     */
    suspend fun generateRefreshToken(
        oauth2: InteractiveFlowSessionOAuth2,
        userId: UUID,
        tokenAudience: String,
        dpopJkt: String? = null
    ) = generateRefreshToken(
        userId = userId,
        clientId = oauth2.clientId,
        tokenAudience = tokenAudience,
        grantedScopes = oauth2.grantedScopes ?: emptyList(),
        grantedAt = oauth2.grantedAt,
        grantedBy = oauth2.grantedBy?.name,
        consentedScopes = oauth2.consentedScopes ?: emptyList(),
        consentedAt = oauth2.consentedAt,
        consentedBy = oauth2.consentedBy?.name,
        clientScopes = emptyList(),
        sessionId = oauth2.sessionId,
        grantType = "authorization_code",
        dpopJkt = dpopJkt
    )

    /**
     * Generate a new refresh token using the information stored in the previous [refreshToken].
     */
    suspend fun generateRefreshToken(
        refreshToken: AuthenticationToken,
        tokenAudience: String,
        dpopJkt: String? = null
    ) = generateRefreshToken(
        userId = refreshToken.userId,
        clientId = refreshToken.clientId,
        tokenAudience = tokenAudience,
        grantedScopes = refreshToken.grantedScopes,
        grantedAt = refreshToken.grantedAt,
        grantedBy = refreshToken.grantedBy?.name,
        consentedScopes = refreshToken.consentedScopes,
        consentedAt = refreshToken.consentedAt,
        consentedBy = refreshToken.consentedBy?.name,
        clientScopes = refreshToken.clientScopes,
        sessionId = refreshToken.sessionId,
        grantType = "refresh_token",
        dpopJkt = dpopJkt
    )

    internal suspend fun generateRefreshToken(
        userId: UUID?,
        clientId: String,
        tokenAudience: String,
        grantedScopes: List<String>,
        grantedAt: java.time.LocalDateTime?,
        grantedBy: String?,
        consentedScopes: List<String>,
        consentedAt: java.time.LocalDateTime?,
        consentedBy: String?,
        clientScopes: List<String>,
        sessionId: UUID?,
        grantType: String,
        dpopJkt: String? = null
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
            sessionId = sessionId,
            grantType = grantType,
            dpopJkt = dpopJkt,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(JwtManager.REFRESH_KEY) {
            entity.id?.toString()?.let(this::jwtID)
            audience(listOf(tokenAudience))
            subject(userId?.toString() ?: clientId)
            issueTime(Date.from(issueDate.toInstant(ZoneOffset.UTC)))
            expirationDate?.toInstant(ZoneOffset.UTC)?.let { expirationTime(Date.from(it)) }
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }
}

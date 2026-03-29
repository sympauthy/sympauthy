package com.sympauthy.business.manager.auth.oauth2

import com.nimbusds.jwt.JWTClaimsSet
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
        userId: UUID,
        dpopJkt: String? = null
    ) = generateAccessToken(
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
        grantType = "authorization_code",
        dpopJkt = dpopJkt
    )

    /**
     * Generate a new access token using the information stored in a [refreshToken].
     */
    suspend fun generateAccessToken(
        refreshToken: AuthenticationToken,
        dpopJkt: String? = null
    ) = generateAccessToken(
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
        grantType = "refresh_token",
        dpopJkt = dpopJkt
    )

    /**
     * Generate an access token for client credentials flow (machine-to-machine).
     * This token is not associated with any end-user.
     */
    suspend fun generateAccessTokenForClient(
        clientId: String,
        clientScopes: List<String>,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        return generateAccessToken(
            userId = null,
            clientId = clientId,
            grantedScopes = emptyList(),
            grantedAt = null,
            grantedBy = null,
            consentedScopes = emptyList(),
            consentedAt = null,
            consentedBy = null,
            clientScopes = clientScopes,
            authorizeAttemptId = null,
            grantType = "client_credentials",
            dpopJkt = dpopJkt
        )
    }

    internal suspend fun generateAccessToken(
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
        grantType: String,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        val authConfig = uncheckedAuthConfig.orThrow()
        val allScopes = grantedScopes + consentedScopes + clientScopes

        val issueDate = LocalDateTime.now()
        val expirationDate = issueDate.plus(authConfig.token.accessExpiration)
        val entity = AuthenticationTokenEntity(
            userId = userId,
            type = ACCESS.name,
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
            dpopJkt = dpopJkt,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(
            name = JwtManager.ACCESS_KEY,
            headers = mapOf("typ" to "at+jwt")
        ) {
            entity.id?.toString()?.let(this::jwtID)
            audience(listOf(authConfig.audience))
            subject(userId?.toString() ?: clientId)
            claim("client_id", clientId)
            claim("scope", allScopes.joinToString(" "))
            dpopJkt?.let { claim("cnf", mapOf("jkt" to it)) }
            issueTime(Date.from(issueDate.toInstant(ZoneOffset.UTC)))
            expirationTime(Date.from(expirationDate.toInstant(ZoneOffset.UTC)))
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }
}

package com.sympauthy.business.manager.auth.oauth2

import com.auth0.jwt.JWTCreator
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.mapper.EncodedAuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.StandardScope
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.util.loggerForClass
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@Singleton
class IdTokenGenerator(
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: EncodedAuthenticationTokenMapper,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    private val logger = loggerForClass()

    /**
     * Generate a new id token containing user info accessible according to the scopes granted in [authorizeAttempt].
     */
    suspend fun generateIdToken(
        authorizeAttempt: CompletedAuthorizeAttempt,
        userId: UUID,
        accessToken: EncodedAuthenticationToken
    ) = generateIdToken(
        userId = userId,
        authorizeAttemptId = authorizeAttempt.id,
        clientId = authorizeAttempt.clientId,
        scopes = authorizeAttempt.grantedScopes,
        nonce = authorizeAttempt.nonce,
        accessToken = accessToken,
        grantType = "authorization_code"
    )

    /**
     * Generate a new access token using the information stored in a [refreshToken].
     */
    suspend fun generateIdToken(
        refreshToken: AuthenticationToken,
        accessToken: EncodedAuthenticationToken
    ) = generateIdToken(
        userId = refreshToken.userId,
        clientId = refreshToken.clientId,
        scopes = refreshToken.scopes,
        authorizeAttemptId = refreshToken.authorizeAttemptId,
        accessToken = accessToken,
        grantType = "refresh_token"
    )

    internal suspend fun generateIdToken(
        userId: UUID?,
        clientId: String,
        scopes: List<String>,
        authorizeAttemptId: UUID?,
        accessToken: EncodedAuthenticationToken,
        nonce: String? = null,
        grantType: String
    ): EncodedAuthenticationToken? {
        // ID tokens are only for user authentication, not client credentials
        if (userId == null) {
            return null
        }

        val authConfig = uncheckedAuthConfig.orThrow()

        // FIXME compute at_hash with accessToken

        val claims = collectedClaimManager.findByUserIdAndReadableByScopes(
            userId = userId,
            scopes = scopes
        )

        val issueDate = LocalDateTime.now()
        val expirationDate = issueDate.plus(authConfig.token.idExpiration)
        val entity = AuthenticationTokenEntity(
            userId = userId,
            type = AuthenticationTokenType.ID.name,
            clientId = clientId,
            scopes = scopes.toTypedArray(),
            authorizeAttemptId = authorizeAttemptId,
            grantType = grantType,
            revoked = false,
            issueDate = issueDate,
            expirationDate = expirationDate
        ).let { tokenRepository.save(it) }

        val encodedToken = jwtManager.create(JwtManager.PUBLIC_KEY) {
            entity.id?.toString()?.let(this::withJWTId)
            // Pretty weird but in OpenID spec, the audience is the client_id of the client which defer from OAuth2 spec.
            // https://openid.net/specs/openid-connect-basic-1_0.html#IDToken
            withAudience(clientId)
            withSubject(userId.toString())
            withIssuedAt(issueDate.toInstant(ZoneOffset.UTC))
            withExpiresAt(expirationDate.toInstant(ZoneOffset.UTC))
            nonce?.let { withClaim("nonce", it) }

            claims.forEach { claim ->
                withClaim(claim)
            }
        }

        return tokenMapper.toEncodedAuthenticationToken(entity, encodedToken)
    }

    internal fun shouldGenerateIdToken(scopes: List<String>): Boolean {
        return scopes.contains(StandardScope.OPENID.scope)
    }

    private fun JWTCreator.Builder.withClaim(claim: CollectedClaim) {
        when (claim.value) { // FIXME add other types
            is String -> withClaim(claim.claim.id, claim.value)
            else -> {
                logger.error("Unable to encode claim '${claim.claim.id}' into id token.")
            }
        }
        if (claim.claim.verifiedId != null) {
            withClaim(claim.claim.verifiedId, claim.verified ?: false)
        }
    }
}

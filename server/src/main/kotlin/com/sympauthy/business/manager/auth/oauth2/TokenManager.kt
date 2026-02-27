package com.sympauthy.business.manager.auth.oauth2

import com.auth0.jwt.interfaces.DecodedJWT
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.exception.LocalizedException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import java.util.*

@Singleton
open class TokenManager(
    @Inject private val jwtManager: JwtManager,
    @Inject private val accessTokenGenerator: AccessTokenGenerator,
    @Inject private val refreshTokenGenerator: RefreshTokenGenerator,
    @Inject private val idTokenGenerator: IdTokenGenerator,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: AuthenticationTokenMapper
) {

    /**
     * Return the [AuthenticationToken] identified by [id], null otherwise.
     */
    suspend fun findById(id: UUID): AuthenticationToken? {
        return tokenRepository.findById(id)?.let(tokenMapper::toToken)
    }

    /**
     * Revoke the token identified by [id].
     * A revoked token cannot be used anymore whether it is for authentication or for refresh.
     */
    suspend fun revokeToken(id: UUID) {
        return tokenRepository.updateRevokedById(id, true)
    }

    @Transactional
    open suspend fun generateTokens(
        authorizeAttempt: CompletedAuthorizeAttempt
    ): GenerateTokenResult = coroutineScope {
        if (authorizeAttempt.expired) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.expired", "description.oauth2.expired")
        }

        val deferredAccessToken = async {
            accessTokenGenerator.generateAccessToken(authorizeAttempt, authorizeAttempt.userId)
        }
        val deferredRefreshToken = async {
            refreshTokenGenerator.generateRefreshToken(authorizeAttempt, authorizeAttempt.userId)
        }

        val accessToken = deferredAccessToken.await()
        val deferredIdToken = async {
            idTokenGenerator.generateIdToken(
                authorizeAttempt = authorizeAttempt,
                userId = authorizeAttempt.userId,
                accessToken = accessToken
            )
        }

        GenerateTokenResult(
            accessToken = accessToken,
            refreshToken = deferredRefreshToken.await(),
            idToken = deferredIdToken.await()
        )
    }

    /**
     * Decodes and verify the [encodedRefreshToken] and issues a new access token.
     *
     * Additionally, a new refresh token may be issued if the refresh token expires
     * before the expiration of the new access token.
     *
     * Throws an [LocalizedException] if the refresh token validation fails:
     * - one of the validation of [JwtManager.decodeAndVerify].
     * - the [client] does not match the one we have issued the token too.
     */
    @Transactional
    open suspend fun refreshToken(
        client: Client,
        encodedRefreshToken: String
    ): List<EncodedAuthenticationToken> = supervisorScope {
        val decodedToken = try {
            jwtManager.decodeAndVerify(REFRESH_KEY, encodedRefreshToken)
        } catch (e: LocalizedException) {
            throw oauth2ExceptionOf(INVALID_GRANT, e.detailsId)
        }

        val refreshToken = getAuthenticationToken(decodedToken)
        if (refreshToken.clientId != client.id) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.mismatching_client")
        }

        val accessToken = accessTokenGenerator.generateAccessToken(refreshToken)
        val refreshedRefreshToken = if (shouldRefreshToken(refreshToken, accessToken)) {
            refreshTokenGenerator.generateRefreshToken(refreshToken)
        } else null

        listOfNotNull(accessToken, refreshedRefreshToken)
    }

    internal fun shouldRefreshToken(
        refreshToken: AuthenticationToken,
        accessToken: EncodedAuthenticationToken
    ): Boolean {
        return when {
            refreshToken.expirationDate == null -> false
            accessToken.expirationDate == null || refreshToken.expirationDate.isBefore(accessToken.expirationDate) -> true
            else -> false
        }
    }

    /**
     * Return the information we stored about the [decodedToken] when we issued it.
     *
     * Throws an [OAuth2Exception] if:
     * - the identifier of the token cannot be decoded.
     * - the token cannot be found in the database despite being signed with our signature.
     * - the token has been revoked.
     */
    suspend fun getAuthenticationToken(decodedToken: DecodedJWT): AuthenticationToken {
        val id = try {
            UUID.fromString(decodedToken.id)
        } catch (e: IllegalArgumentException) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.invalid_token_id")
        }
        val token = findById(id)

        // For client credentials tokens, subject is the client ID
        // For user tokens, subject is the user ID
        val expectedSubject = token?.userId?.toString() ?: token?.clientId

        return when {
            token == null -> throw oauth2ExceptionOf(INVALID_GRANT, "token.invalid_token_id")
            token.revoked -> throw oauth2ExceptionOf(INVALID_GRANT, "token.revoked")
            decodedToken.subject != expectedSubject -> throw oauth2ExceptionOf(
                INVALID_GRANT, "token.invalid_token_id"
            )

            else -> token
        }
    }
}

data class GenerateTokenResult(
    val accessToken: EncodedAuthenticationToken,
    val refreshToken: EncodedAuthenticationToken?,
    val idToken: EncodedAuthenticationToken?
)

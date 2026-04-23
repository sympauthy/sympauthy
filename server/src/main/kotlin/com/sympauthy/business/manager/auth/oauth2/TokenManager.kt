package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.TokenRevokedBy
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.exception.LocalizedException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import java.time.LocalDateTime
import java.util.*

@Singleton
open class TokenManager(
    @Inject private val jwtManager: JwtManager,
    @Inject private val accessTokenGenerator: AccessTokenGenerator,
    @Inject private val refreshTokenGenerator: RefreshTokenGenerator,
    @Inject private val idTokenGenerator: IdTokenGenerator,
    @Inject private val consentManager: ConsentManager,
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
     * Revoke all tokens issued to [userId], regardless of client.
     * Returns the number of tokens revoked.
     */
    @Transactional
    open suspend fun revokeTokensByUser(userId: UUID, revokedBy: TokenRevokedBy, revokedById: UUID?): Int {
        return tokenRepository.updateRevokedAtByUserId(
            userId = userId,
            revokedAt = LocalDateTime.now(),
            revokedBy = revokedBy.name,
            revokedById = revokedById
        )
    }

    /**
     * Revoke all tokens issued to [userId] for [clientId].
     * Returns the number of tokens revoked.
     */
    @Transactional
    open suspend fun revokeTokensByUserAndClient(
        userId: UUID,
        clientId: String,
        revokedBy: TokenRevokedBy,
        revokedById: UUID?
    ): Int {
        return tokenRepository.updateRevokedAtByUserIdAndClientId(
            userId = userId,
            clientId = clientId,
            revokedAt = LocalDateTime.now(),
            revokedBy = revokedBy.name,
            revokedById = revokedById
        )
    }

    /**
     * Generate tokens for a completed authorization code flow.
     *
     * Always generates an access token and an ID token. A refresh token is only generated if the [client] supports
     * the [GrantType.REFRESH_TOKEN] grant type.
     *
     * @throws OAuth2Exception if the [authorizeAttempt] has expired.
     */
    @Transactional
    open suspend fun generateTokens(
        authorizeAttempt: CompletedAuthorizeAttempt,
        client: Client,
        dpopJkt: String? = null
    ): GenerateTokenResult = coroutineScope {
        if (authorizeAttempt.expired) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.expired", "description.oauth2.expired")
        }

        val tokenAudience = client.audience.tokenAudience
        val deferredAccessToken = async {
            accessTokenGenerator.generateAccessToken(authorizeAttempt, authorizeAttempt.userId, tokenAudience, dpopJkt = dpopJkt)
        }
        val deferredRefreshToken = if (client.supportsGrantType(GrantType.REFRESH_TOKEN)) {
            async {
                refreshTokenGenerator.generateRefreshToken(authorizeAttempt, authorizeAttempt.userId, tokenAudience, dpopJkt = dpopJkt)
            }
        } else null

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
            refreshToken = deferredRefreshToken?.await(),
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
        encodedRefreshToken: String,
        dpopJkt: String? = null
    ): List<EncodedAuthenticationToken> = supervisorScope {
        val decodedToken = try {
            jwtManager.decodeAndVerify(REFRESH_KEY, encodedRefreshToken)
        } catch (e: LocalizedException) {
            throw oauth2ExceptionOf(INVALID_GRANT, e.detailsId)
        }

        val refreshToken = getAuthenticationToken(decodedToken)
        if (refreshToken.clientId != client.id) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.mismatching_client", "description.token.mismatching_client")
        }

        // If the refresh token was DPoP-bound, the new proof must use the same key
        if (refreshToken.dpopJkt != null) {
            if (dpopJkt == null) {
                throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.missing_header")
            }
            if (refreshToken.dpopJkt != dpopJkt) {
                throw oauth2ExceptionOf(INVALID_DPOP_PROOF, "dpop.mismatching_key")
            }
        }

        // For user tokens, verify the consent has not been revoked (checked at audience level)
        if (refreshToken.userId != null) {
            consentManager.findActiveConsentByAudienceOrNull(refreshToken.userId, client.audience.id)
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "token.consent_revoked", "description.token.consent_revoked")
        }

        // Use the DPoP jkt from the proof, or carry forward the existing binding
        val effectiveDpopJkt = dpopJkt ?: refreshToken.dpopJkt
        val tokenAudience = client.audience.tokenAudience

        val accessToken = accessTokenGenerator.generateAccessToken(refreshToken, tokenAudience, dpopJkt = effectiveDpopJkt)
        val refreshedRefreshToken = if (shouldRefreshToken(refreshToken, accessToken)) {
            refreshTokenGenerator.generateRefreshToken(refreshToken, tokenAudience, dpopJkt = effectiveDpopJkt)
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
     * Decode and revoke the [encodedToken] issued to [client].
     *
     * Per RFC 7009, this method does not throw if the token is invalid, expired, already revoked, or not found.
     * Only tokens owned by [client] are revoked.
     *
     * If the token is a refresh token, all tokens associated to the same session ([AuthenticationToken.authorizeAttemptId])
     * are revoked as well (cascading revocation).
     */
    @Transactional
    open suspend fun revokeTokenByEncodedToken(
        client: Client,
        encodedToken: String,
        tokenTypeHint: String?
    ) {
        val decodedToken = when (tokenTypeHint) {
            "access_token" -> jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, encodedToken)
            "refresh_token" -> jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, encodedToken)
            else -> when (jwtManager.getKeyIdOrNull(encodedToken)) {
                ACCESS_KEY -> jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, encodedToken)
                REFRESH_KEY -> jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, encodedToken)
                else -> null
            }
        } ?: return

        val tokenId = try {
            UUID.fromString(decodedToken.id)
        } catch (e: IllegalArgumentException) {
            return
        }

        val token = findById(tokenId) ?: return
        if (token.clientId != client.id) return

        val now = LocalDateTime.now()
        if (token.type == REFRESH && token.authorizeAttemptId != null) {
            tokenRepository.updateRevokedAtByAuthorizeAttemptId(
                authorizeAttemptId = token.authorizeAttemptId,
                revokedAt = now,
                revokedBy = TokenRevokedBy.CLIENT.name,
                revokedById = null
            )
        } else {
            tokenRepository.updateRevokedAt(
                id = token.id,
                revokedAt = now,
                revokedBy = TokenRevokedBy.CLIENT.name,
                revokedById = null
            )
        }
    }

    /**
     * Introspect the [encodedToken] and return the stored [AuthenticationToken] if the token is active,
     * or `null` if the token is invalid, expired, revoked, or not owned by [client].
     *
     * The [tokenTypeHint] is an optional hint about the token type (`access_token` or `refresh_token`)
     * used to select the correct signing key for verification.
     */
    suspend fun introspectToken(
        client: Client,
        encodedToken: String,
        tokenTypeHint: String?
    ): AuthenticationToken? {
        val decodedToken = when (tokenTypeHint) {
            "access_token" -> jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, encodedToken)
            "refresh_token" -> jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, encodedToken)
            else -> when (jwtManager.getKeyIdOrNull(encodedToken)) {
                ACCESS_KEY -> jwtManager.decodeAndVerifyOrNull(ACCESS_KEY, encodedToken)
                REFRESH_KEY -> jwtManager.decodeAndVerifyOrNull(REFRESH_KEY, encodedToken)
                else -> null
            }
        } ?: return null

        val tokenId = try {
            UUID.fromString(decodedToken.id)
        } catch (e: IllegalArgumentException) {
            return null
        }

        val token = findById(tokenId) ?: return null
        if (token.revoked) return null
        if (token.clientId != client.id) return null
        return token
    }

    /**
     * Return the information we stored about the [decodedToken] when we issued it.
     *
     * Throws an [OAuth2Exception] if:
     * - the identifier of the token cannot be decoded.
     * - the token cannot be found in the database despite being signed with our signature.
     * - the token has been revoked.
     */
    suspend fun getAuthenticationToken(decodedToken: DecodedJwt): AuthenticationToken {
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

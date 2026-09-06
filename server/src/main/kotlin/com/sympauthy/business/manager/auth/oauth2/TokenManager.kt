package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.toOAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.securitycontext.AccessReviewManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_DPOP_PROOF
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.TokenRevokedBy
import com.sympauthy.business.model.securitycontext.AccessReviewDecision
import com.sympauthy.business.model.securitycontext.AccessReviewReason
import com.sympauthy.business.model.securitycontext.ObservedRequest
import com.sympauthy.data.repository.AuthenticationTokenRepository
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
    @Inject private val userManager: UserManager,
    @Inject private val actorTokenValidator: ActorTokenValidator,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: AuthenticationTokenMapper,
    @Inject private val accessReviewManager: AccessReviewManager
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
     * the [GrantType.REFRESH_TOKEN] grant type. A [session] that has expired issues nothing and throws an
     * `OAuth2Exception` carrying `token.expired`.
     */
    @Transactional
    open suspend fun generateTokens(
        session: CompletedInteractiveFlowSession,
        oauth2: InteractiveFlowSessionOAuth2,
        client: Client,
        dpopJkt: String? = null
    ): GenerateTokenResult = coroutineScope {
        if (session.expired) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.expired", "description.oauth2.expired")
        }
        // Never mint a token for an account a sign-up has not finished, however the session reached this
        // point: a token is what turns an account into one another system will act on. Answered as the
        // grant being invalid rather than as a business failure — this surface owes its caller an RFC 6749
        // error object, the same reason TokenExchangeManager.resolveTargetUser refuses its own way.
        checkPromotedOrInvalidGrant(session.userId)

        val tokenAudience = client.audience.tokenAudience
        val deferredAccessToken = async {
            accessTokenGenerator.generateAccessToken(
                oauth2,
                session.userId,
                tokenAudience,
                dpopJkt = dpopJkt
            )
        }
        val deferredRefreshToken = if (client.supportsGrantType(GrantType.REFRESH_TOKEN)) {
            async {
                refreshTokenGenerator.generateRefreshToken(
                    oauth2,
                    session.userId,
                    tokenAudience,
                    dpopJkt = dpopJkt
                )
            }
        } else null

        val accessToken = deferredAccessToken.await()
        val deferredIdToken = async {
            idTokenGenerator.generateIdToken(
                oauth2 = oauth2,
                userId = session.userId,
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
     * Throws an [OAuth2Exception] carrying `invalid_grant` if the refresh token validation fails:
     * - one of the validation of [JwtManager.decodeAndVerify].
     * - the [client] does not match the one we have issued the token too.
     *
     * A failure of this server rather than of the token — a signing key that will not load — travels out as
     * itself, so that it is answered as the `5xx` it is.
     */
    @Transactional
    open suspend fun refreshToken(
        client: Client,
        encodedRefreshToken: String,
        dpopJkt: String? = null,
        observedRequest: ObservedRequest? = null
    ): List<EncodedAuthenticationToken> = supervisorScope {
        val decodedToken = try {
            jwtManager.decodeAndVerify(REFRESH_KEY, encodedRefreshToken)
        } catch (e: InvalidJwtException) {
            throw e.toOAuth2Exception(INVALID_GRANT)
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

        // For user tokens, verify the account is one this server finished creating and the consent has not
        // been revoked (checked at audience level). A client-credentials token carries no user.
        if (refreshToken.userId != null) {
            checkPromotedOrInvalidGrant(refreshToken.userId)
            consentManager.findActiveConsentByAudienceOrNull(refreshToken.userId, client.audience.id)
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "token.consent_revoked", "description.token.consent_revoked")
            if (observedRequest != null) {
                reviewAccessOrThrow(client, refreshToken, observedRequest)
            }
        }

        // Use the DPoP jkt from the proof, or carry forward the existing binding
        val effectiveDpopJkt = dpopJkt ?: refreshToken.dpopJkt
        val tokenAudience = client.audience.tokenAudience

        val accessToken =
            accessTokenGenerator.generateAccessToken(refreshToken, tokenAudience, dpopJkt = effectiveDpopJkt)
        val refreshedRefreshToken = if (shouldRefreshToken(refreshToken, accessToken)) {
            refreshTokenGenerator.generateRefreshToken(refreshToken, tokenAudience, dpopJkt = effectiveDpopJkt)
        } else null

        listOfNotNull(accessToken, refreshedRefreshToken)
    }

    /**
     * Refuse [userId] unless it is an account this server has finished creating, as `invalid_grant`.
     *
     * [UserManager.checkPromoted] throws a business failure, which is right for a caller that gets one; the
     * token endpoint gets an OAuth2 error object, and a grant naming an account that does not exist is an
     * invalid grant.
     */
    private suspend fun checkPromotedOrInvalidGrant(userId: UUID) {
        try {
            userManager.checkPromoted(userId)
        } catch (_: BusinessException) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.invalid_user", "description.token.invalid_user")
        }
    }

    internal fun shouldRefreshToken(
        refreshToken: AuthenticationToken,
        accessToken: EncodedAuthenticationToken
    ): Boolean {
        return when {
            refreshToken.expirationDate == null -> false
            accessToken.expirationDate == null || refreshToken.expirationDate.isBefore(
                accessToken.expirationDate
            ) -> true
            else -> false
        }
    }

    /**
     * Refuse the refresh where [client]'s access review of the place [observedRequest] came from said
     * to, revoking the whole sign-in first where it said that.
     *
     * A refusal is `invalid_grant`, which is the same answer as a token that has been revoked: the
     * person is told to sign in again, and the client is told nothing about why beyond that.
     */
    private suspend fun reviewAccessOrThrow(
        client: Client,
        refreshToken: AuthenticationToken,
        observedRequest: ObservedRequest
    ) {
        val decision = accessReviewManager.reviewAccess(
            client = client,
            userId = refreshToken.userId!!,
            reason = AccessReviewReason.REFRESH_TOKEN,
            observedRequest = observedRequest
        )
        when (decision) {
            AccessReviewDecision.ALLOW -> Unit
            AccessReviewDecision.DENY -> throw accessReviewRefusal()
            AccessReviewDecision.REVOKE_SESSION -> {
                refreshToken.sessionId?.let { revokeSessionTokens(it) }
                throw accessReviewRefusal()
            }
        }
    }

    private fun accessReviewRefusal() = oauth2ExceptionOf(
        INVALID_GRANT, "token.access_review_denied", "description.token.access_review_denied"
    )

    /**
     * Revoke every token issued to the sign-in [sessionId] identifies, which is what a client asking
     * for the session to be revoked gets: the whole lineage of tokens that sign-in produced, and not
     * only the one that was presented.
     */
    @Transactional
    open suspend fun revokeSessionTokens(sessionId: UUID) {
        tokenRepository.updateRevokedAtBySessionId(
            sessionId = sessionId,
            revokedAt = LocalDateTime.now(),
            revokedBy = TokenRevokedBy.ACCESS_REVIEW.name,
            revokedById = null
        )
    }

    /**
     * Decode and revoke the [encodedToken] issued to [client].
     *
     * Per RFC 7009, this method does not throw if the token is invalid, expired, already revoked, or not found.
     * Only tokens owned by [client] are revoked.
     *
     * If the token is a refresh token, all tokens associated to the same session ([AuthenticationToken.sessionId])
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
        } catch (_: IllegalArgumentException) {
            return
        }

        val token = findById(tokenId) ?: return
        if (token.clientId != client.id) return

        val now = LocalDateTime.now()
        if (token.type == REFRESH && token.sessionId != null) {
            tokenRepository.updateRevokedAtBySessionId(
                sessionId = token.sessionId,
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

        // RFC 8693: revoking a token also revokes every act-as token derived from it (subject_token provenance).
        tokenRepository.updateRevokedAtByActorTokenId(
            actorTokenId = token.id,
            revokedAt = now,
            revokedBy = TokenRevokedBy.CLIENT.name,
            revokedById = null
        )
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
        } catch (_: IllegalArgumentException) {
            return null
        }

        val token = findById(tokenId) ?: return null
        if (token.revoked) return null
        if (token.clientId != client.id) return null
        return try {
            actorTokenValidator.validateActorToken(token)
            token
        } catch (_: OAuth2Exception) {
            null
        }
    }

    /**
     * Return the information we stored about the [decodedToken] when we issued it.
     *
     * Throws an [OAuth2Exception] if:
     * - the identifier of the token cannot be decoded.
     * - the token cannot be found in the database despite being signed with our signature.
     * - the token has been revoked.
     * - the token is an act-as token (RFC 8693) whose actor token is no longer valid (missing, revoked, or expired).
     */
    suspend fun getAuthenticationToken(decodedToken: DecodedJwt): AuthenticationToken {
        val id = try {
            UUID.fromString(decodedToken.id)
        } catch (_: IllegalArgumentException) {
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

            else -> {
                actorTokenValidator.validateActorToken(token)
                token
            }
        }
    }
}

data class GenerateTokenResult(
    val accessToken: EncodedAuthenticationToken,
    val refreshToken: EncodedAuthenticationToken?,
    val idToken: EncodedAuthenticationToken?
)

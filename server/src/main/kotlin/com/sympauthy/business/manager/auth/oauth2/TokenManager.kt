package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.toOAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.AuthenticationTokenType.REFRESH
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.TokenRevokedBy
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
    @Inject private val dpopManager: DpopManager,
    @Inject private val authorizationCodeManager: AuthorizationCodeManager,
    @Inject private val lockManager: LockManager,
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
     * Revoke every token issued to [userId] that is still live, regardless of client, and return how many
     * there were. A token already revoked keeps who revoked it and when, and an expired one is left as it
     * lapsed; neither is counted.
     */
    @Transactional
    open suspend fun revokeTokensByUser(userId: UUID, revokedBy: TokenRevokedBy, revokedById: UUID?): Int {
        return lockManager.withLock(LockKey.User(userId)) {
            tokenRepository.updateRevokedAtByUserId(
                userId = userId,
                revokedAt = LocalDateTime.now(),
                revokedBy = revokedBy.name,
                revokedById = revokedById
            )
        }
    }

    /**
     * Revoke every token issued to [userId] for [clientId] that is still live, and return how many there
     * were. A token already revoked keeps who revoked it and when, and an expired one is left as it lapsed;
     * neither is counted.
     */
    @Transactional
    open suspend fun revokeTokensByUserAndClient(
        userId: UUID,
        clientId: String,
        revokedBy: TokenRevokedBy,
        revokedById: UUID?
    ): Int {
        return lockManager.withLock(LockKey.User(userId)) {
            tokenRepository.updateRevokedAtByUserIdAndClientIdIn(
                userId = userId,
                clientIds = listOf(clientId),
                revokedAt = LocalDateTime.now(),
                revokedBy = revokedBy.name,
                revokedById = revokedById
            )
        }
    }

    /**
     * Exchange the authorization code [session] was handed for tokens, as [generateTokensOrNull] does.
     *
     * Throws an `OAuth2Exception` carrying `invalid_grant` and `code.already_exchanged` where a client
     * already exchanged that code, once every token issued under [session] is revoked.
     */
    suspend fun generateTokens(
        session: CompletedInteractiveFlowSession,
        oauth2: InteractiveFlowSessionOAuth2,
        client: Client,
        dpopJkt: String? = null
    ): GenerateTokenResult {
        return generateTokensOrNull(session, oauth2, client, dpopJkt)
            ?: throw oauth2ExceptionOf(INVALID_GRANT, "code.already_exchanged", "description.oauth2.replay")
    }

    /**
     * Mark the authorization code [session] was handed as exchanged, and generate the tokens it is
     * exchanged for.
     *
     * Always generates an access token. An id token is generated where the grant [oauth2] records carries
     * the `openid` scope, and a refresh token where the [client] supports the [GrantType.REFRESH_TOKEN]
     * grant type. A [session] that has expired issues nothing and throws an `OAuth2Exception` carrying
     * `token.expired`.
     *
     * Answers null where a client already exchanged the code, having issued nothing and revoked every
     * token issued under [session] — the earlier exchange's, and every refresh descended from them — as
     * RFC 6749 §4.1.2 recommends.
     *
     * The replay is answered rather than thrown so that the revocation commits with the transaction. The
     * code is marked before anything is issued, and in the transaction issuing it: an exchange racing this
     * one waits on the row until these tokens are committed, and then finds them to revoke.
     */
    @Transactional
    internal open suspend fun generateTokensOrNull(
        session: CompletedInteractiveFlowSession,
        oauth2: InteractiveFlowSessionOAuth2,
        client: Client,
        dpopJkt: String? = null
    ): GenerateTokenResult? = coroutineScope {
        if (session.expired) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.expired", "description.oauth2.expired")
        }
        // Never mint a token for an account a sign-up has not finished, however the session reached this
        // point: a token is what turns an account into one another system will act on. Answered as the
        // grant being invalid rather than as a business failure — this surface owes its caller an RFC 6749
        // error object, the same reason TokenExchangeManager.resolveTargetUser refuses its own way.
        checkPromotedOrInvalidGrant(session.userId)

        if (!authorizationCodeManager.markExchanged(session)) {
            lockManager.withLock(LockKey.User(session.userId)) {
                tokenRepository.updateRevokedAtBySessionId(
                    sessionId = session.id,
                    revokedAt = LocalDateTime.now(),
                    revokedBy = TokenRevokedBy.AUTHORIZATION_CODE_REPLAYED.name,
                    revokedById = null
                )
            }
            return@coroutineScope null
        }

        val tokenAudience = client.audience.tokenAudience
        val deferredAccessToken = async {
            accessTokenGenerator.generateAccessToken(
                oauth2,
                session.userId,
                client.audience,
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
                audienceId = client.audience.id,
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
     * before the expiration of the new access token, and a new id token is issued when the grant the
     * [encodedRefreshToken] came from carried the `openid` scope.
     *
     * Throws an [OAuth2Exception] carrying `invalid_grant` if the refresh token validation fails:
     * - one of the validation of [JwtManager.decodeAndVerify].
     * - `token.invalid_token_id`, where the token names no row of ours, or names one whose subject it
     *   does not match.
     * - `token.revoked`, where the row it names has been revoked.
     * - the [client] does not match the one we have issued the token too.
     * - `token.invalid_user`, where the account the token names is one no sign-up finished.
     * - `token.consent_revoked`, where the consent the grant rests on has been revoked.
     *
     * Throws an [OAuth2Exception] carrying `invalid_dpop_proof` where the token is bound to a key and
     * [dpopJkt] is absent (`dpop.missing_header`) or names a different one (`dpop.mismatching_key`).
     *
     * A failure of this server rather than of the token — a signing key that will not load — travels out as
     * itself, so that it is answered as the `5xx` it is.
     *
     * Every check is made, and every token issued, holding the lock over the account the token belongs to,
     * which every revocation of that account's tokens takes too. A revocation racing this refresh either
     * commits first, and is read here as the refusal it is, or waits for these tokens to be committed and
     * revokes them with the rest.
     */
    @Transactional
    open suspend fun refreshToken(
        client: Client,
        encodedRefreshToken: String,
        dpopJkt: String? = null
    ): GenerateTokenResult = supervisorScope {
        val decodedToken = try {
            jwtManager.decodeAndVerify(REFRESH_KEY, encodedRefreshToken)
        } catch (e: InvalidJwtException) {
            throw e.toOAuth2Exception(INVALID_GRANT)
        }
        val lockKeys = listOfNotNull(getAuthenticationToken(decodedToken).userId?.let(LockKey::User))

        lockManager.withLock(*lockKeys.toTypedArray()) {
            refreshHeldToken(client, decodedToken, dpopJkt)
        }
    }

    /**
     * The body of [refreshToken], run once the lock over the account [decodedToken] belongs to is held.
     *
     * The token is read again here rather than handed in, because the read made to find the account came
     * before the wait: a revocation committed during it is only seen by a read made after.
     */
    internal suspend fun refreshHeldToken(
        client: Client,
        decodedToken: DecodedJwt,
        dpopJkt: String?
    ): GenerateTokenResult {
        val refreshToken = getAuthenticationToken(decodedToken)
        if (refreshToken.clientId != client.id) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.mismatching_client", "description.token.mismatching_client")
        }

        dpopManager.checkProofMatchesBinding(refreshToken, dpopJkt)

        // For user tokens, verify the account is one this server finished creating and the consent has not
        // been revoked (checked at audience level). A client-credentials token carries no user.
        if (refreshToken.userId != null) {
            checkPromotedOrInvalidGrant(refreshToken.userId)
            consentManager.findActiveConsentByAudienceOrNull(refreshToken.userId, client.audience.id)
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "token.consent_revoked", "description.token.consent_revoked")
        }

        // Use the DPoP jkt from the proof, or carry forward the existing binding
        val effectiveDpopJkt = dpopJkt ?: refreshToken.dpopJkt
        val tokenAudience = client.audience.tokenAudience

        val accessToken =
            accessTokenGenerator.generateAccessToken(refreshToken, client.audience, dpopJkt = effectiveDpopJkt)
        val refreshedRefreshToken = if (shouldRefreshToken(refreshToken, accessToken)) {
            refreshTokenGenerator.generateRefreshToken(refreshToken, tokenAudience, dpopJkt = effectiveDpopJkt)
        } else null
        // A refresh reissues the identity because OpenID Connect Core §12.2 lets it, and a client holding
        // a refreshed access token with a stale id token has no way to learn the subject changed. Whether
        // this grant is owed one is the generator's to answer, so that the authorization code cannot
        // answer it differently.
        val idToken = idTokenGenerator.generateIdToken(refreshToken, client.audience.id, accessToken)

        return GenerateTokenResult(
            accessToken = accessToken,
            refreshToken = refreshedRefreshToken,
            idToken = idToken
        )
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
        encodedToken: String
    ) {
        val decodedToken = decodeAndVerifyAccessOrRefreshTokenOrNull(encodedToken) ?: return

        val tokenId = try {
            UUID.fromString(decodedToken.id)
        } catch (_: IllegalArgumentException) {
            return
        }

        val token = findById(tokenId) ?: return
        if (token.clientId != client.id) return

        val now = LocalDateTime.now()
        lockManager.withLock(*listOfNotNull(token.userId?.let(LockKey::User)).toTypedArray()) {
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
    }

    /**
     * Introspect the [encodedToken] and return the stored [AuthenticationToken] if the token is active,
     * or `null` if the token is invalid, expired, revoked, not owned by [client], or not of [type].
     *
     * A null [type] accepts an access token and a refresh token alike.
     */
    suspend fun introspectToken(
        client: Client,
        encodedToken: String,
        type: AuthenticationTokenType?
    ): AuthenticationToken? {
        val decodedToken = decodeAndVerifyAccessOrRefreshTokenOrNull(encodedToken) ?: return null

        val tokenId = try {
            UUID.fromString(decodedToken.id)
        } catch (_: IllegalArgumentException) {
            return null
        }

        val token = findById(tokenId) ?: return null
        if (type != null && token.type != type) return null
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
     * Decode and verify the [encodedToken] against the key its header names, provided that key is the one
     * access tokens or the one refresh tokens are signed with. Null otherwise, or where it does not verify.
     *
     * The type is read off the token and never off the `token_type_hint` a client sends beside it: RFC 7009
     * §2.1 and RFC 7662 §2.1 let a server able to detect the type ignore the hint, and a hint naming the
     * wrong type would otherwise leave a live token unrecognized.
     */
    internal suspend fun decodeAndVerifyAccessOrRefreshTokenOrNull(encodedToken: String): DecodedJwt? {
        return when (val keyId = jwtManager.getKeyIdOrNull(encodedToken)) {
            ACCESS_KEY, REFRESH_KEY -> jwtManager.decodeAndVerifyOrNull(keyId, encodedToken)
            else -> null
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

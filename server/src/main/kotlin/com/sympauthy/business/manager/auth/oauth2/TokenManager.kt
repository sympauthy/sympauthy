package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.toOAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.InvalidJwtException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.ActorGrantValidator
import com.sympauthy.business.manager.auth.GrantManager
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
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.data.repository.AuthenticationTokenRepository
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
    @Inject private val consentManager: ConsentManager,
    @Inject private val userManager: UserManager,
    @Inject private val actorGrantValidator: ActorGrantValidator,
    @Inject private val dpopManager: DpopManager,
    @Inject private val authorizationCodeManager: AuthorizationCodeManager,
    @Inject private val grantManager: GrantManager,
    @Inject private val lockManager: LockManager,
    @Inject private val tokenRepository: AuthenticationTokenRepository,
    @Inject private val tokenMapper: AuthenticationTokenMapper
) {

    /**
     * Return the [AuthenticationToken] identified by [id], with the grant it was issued under, null otherwise.
     *
     * A grant is deleted only in the transaction deleting its tokens, so a grant missing behind a token read
     * before that transaction committed is one collected since, and the token is answered as gone with it.
     */
    suspend fun findById(id: UUID): AuthenticationToken? {
        val entity = tokenRepository.findById(id) ?: return null
        val grant = grantManager.findByIdOrNull(entity.grantId)
        if (grant == null && tokenRepository.findById(id) == null) {
            return null
        }
        return tokenMapper.toToken(entity, grant)
    }

    /**
     * Exchange [code] for the tokens of the user its [grant] was opened for, as [generateTokensForUserOrNull]
     * does.
     *
     * Throws an `OAuth2Exception` carrying `invalid_grant` and `code.already_exchanged` where a client
     * already exchanged [code], once the grant it names is revoked.
     */
    suspend fun generateTokensForUser(
        code: AuthorizationCode,
        grant: Grant,
        client: Client,
        dpopJkt: String? = null
    ): GenerateTokenResult {
        return generateTokensForUserOrNull(code, grant, client, dpopJkt)
            ?: throw oauth2ExceptionOf(INVALID_GRANT, "code.already_exchanged", "description.oauth2.replay")
    }

    /**
     * Mark [code] as exchanged, and generate the tokens it is exchanged for under the grant it names. [grant]
     * is that grant as the caller read it, which names the account whose lock is taken; it is read again once
     * the lock is held.
     *
     * Throws `code.missing_user`, internal, where [grant] names no user: an authorization opens its grant for
     * the person it signed in, so one without is this server's failure.
     *
     * Always generates an access token. An id token is generated where the grant carries the `openid`
     * scope, repeating the `nonce` [code] carries, and a refresh token where the [client] supports the
     * [GrantType.REFRESH_TOKEN] grant type. A [code] that has expired issues nothing and throws an
     * `OAuth2Exception` carrying `token.expired`, and so does a grant that is no longer live, as [checkLive]
     * says, leaving the code unspent.
     *
     * Answers null where a client already exchanged the code, having issued nothing and revoked its grant —
     * the earlier exchange's tokens, and every refresh descended from them — as RFC 6749 §4.1.2 recommends.
     *
     * The replay is answered rather than thrown so that the revocation commits with the transaction. The
     * code is marked before anything is issued, and in the transaction issuing it: an exchange racing this
     * one waits on the row until these tokens are committed, and then revokes the grant they stand under.
     *
     * The grant is read, and the tokens written, holding the lock over the account every revocation of its
     * grants takes too, as a refresh does: a revocation either commits first and is read here as the refusal
     * it is, or waits for these tokens and finds the grant live through them.
     *
     * The code itself is not read again. A code collected since it was read while its grant is still there was
     * one already exchanged — a grant is issued tokens through its code alone — so marking it answers false,
     * and it is answered as the replay it is.
     */
    @Transactional
    internal open suspend fun generateTokensForUserOrNull(
        code: AuthorizationCode,
        grant: Grant,
        client: Client,
        dpopJkt: String? = null
    ): GenerateTokenResult? {
        val userId = grant.userId
            ?: throw internalBusinessExceptionOf("code.missing_user", "grantId" to grant.id.toString())
        if (code.expired) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.expired", "description.oauth2.expired")
        }
        // Never mint a token for an account a sign-up has not finished, however the code reached this point: a
        // token is what turns an account into one another system will act on. Answered as the grant being
        // invalid rather than as a business failure — this surface owes its caller an RFC 6749 error object,
        // the same reason TokenExchangeManager.resolveTargetUser refuses its own way.
        checkPromotedOrInvalidGrant(userId)

        return lockManager.withLock(listOf(LockKey.User(userId))) {
            val liveGrant = fetchGrantOfCode(code)
            if (!authorizationCodeManager.markExchanged(code)) {
                grantManager.revokeGrant(liveGrant, GrantRevokedBy.AUTHORIZATION_CODE_REPLAYED, revokedById = null)
                return@withLock null
            }
            checkLive(liveGrant)
            generateTokensUnder(liveGrant, code.nonce, client, dpopJkt)
        }
    }

    /**
     * Generate the tokens an authorization code is exchanged for under its [grant], once the code is spent,
     * the id token repeating the [nonce] the code carries.
     */
    internal suspend fun generateTokensUnder(
        grant: Grant,
        nonce: String?,
        client: Client,
        dpopJkt: String?
    ): GenerateTokenResult = coroutineScope {
        val deferredAccessToken = async {
            accessTokenGenerator.generateAccessToken(
                grant = grant,
                audience = client.audience,
                dpopJkt = dpopJkt
            )
        }
        val deferredRefreshToken = if (client.supportsGrantType(GrantType.REFRESH_TOKEN)) {
            async {
                refreshTokenGenerator.generateRefreshToken(
                    grant = grant,
                    audience = client.audience,
                    dpopJkt = dpopJkt
                )
            }
        } else null

        val accessToken = deferredAccessToken.await()
        val deferredIdToken = async {
            idTokenGenerator.generateIdToken(
                grant = grant,
                audienceId = client.audience.id,
                accessToken = accessToken,
                nonce = nonce
            )
        }

        GenerateTokenResult(
            accessToken = accessToken,
            refreshToken = deferredRefreshToken?.await(),
            idToken = deferredIdToken.await()
        )
    }

    /**
     * The grant [code] names, read again once the lock over its account is held: a revocation committed while
     * the lock was awaited is only seen by a read made after.
     *
     * Throws an `OAuth2Exception` carrying `invalid_grant` and `token.expired` where the grant is gone. A grant
     * is kept as long as its code is, so one gone is a code collected since it was read: it had expired, and is
     * refused as one.
     */
    internal suspend fun fetchGrantOfCode(code: AuthorizationCode): Grant {
        return grantManager.findByIdOrNull(code.grantId)
            ?: throw oauth2ExceptionOf(INVALID_GRANT, "token.expired", "description.oauth2.expired")
    }

    /**
     * Refuse [grant] unless it is live, as an `OAuth2Exception` carrying `invalid_grant`: `grant.revoked`
     * where it has been revoked, and `grant.expired` where it is past its lifetime.
     */
    internal fun checkLive(grant: Grant) {
        when {
            grant.revoked -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.revoked")
            grant.expired -> throw oauth2ExceptionOf(INVALID_GRANT, "grant.expired", "description.grant.expired")
        }
    }

    /**
     * Open the grant a `client_credentials` request from [client] is answered under, holding the
     * [clientScopes] granted to it, and issue the access token it is answered with.
     *
     * Passing [dpopJkt] binds the token to that JWK thumbprint, and leaving it null issues a bearer token.
     */
    @Transactional
    open suspend fun generateAccessTokenForClient(
        client: Client,
        clientScopes: List<String>,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        val grant = grantManager.createClientCredentialsGrant(client.id, client.audience.id, clientScopes)
        return accessTokenGenerator.generateAccessToken(
            grant = grant,
            audience = client.audience,
            dpopJkt = dpopJkt
        )
    }

    /**
     * Decodes and verify the [encodedRefreshToken] and issues a new access token under its grant.
     *
     * Additionally, a new refresh token may be issued if the refresh token expires
     * before the expiration of the new access token, and a new id token is issued when the grant the
     * [encodedRefreshToken] came from carried the `openid` scope.
     *
     * Throws an [OAuth2Exception] carrying `invalid_grant` if the refresh token validation fails:
     * - one of the validation of [JwtManager.decodeAndVerify].
     * - `token.invalid_token_id`, where the token names no row of ours, or names one whose subject it
     *   does not match.
     * - `grant.revoked`, where the grant it was issued under has been revoked.
     * - `grant.expired`, where the grant it was issued under is past its lifetime.
     * - the [client] does not match the one we have issued the token too.
     * - `grant.mismatching_audience`, where the [client] no longer belongs to the audience the grant was opened
     *   for.
     * - `grant.invalid_user`, where the account the token names is one no sign-up finished.
     * - `grant.consent_revoked`, where the consent the grant rests on has been revoked.
     *
     * Throws an [OAuth2Exception] carrying `invalid_dpop_proof` where the token is bound to a key and
     * [dpopJkt] is absent (`dpop.missing_header`) or names a different one (`dpop.mismatching_key`).
     *
     * A failure of this server rather than of the token — a signing key that will not load — travels out as
     * itself, so that it is answered as the `5xx` it is.
     *
     * Every check is made, and every token issued, holding the lock over the account the grant belongs to,
     * which every revocation of that account's grants takes too. A revocation racing this refresh either
     * commits first, and is read here as the refusal it is, or waits for these tokens to be committed and
     * finds the grant live through them.
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
        val lockKeys = listOfNotNull(getAuthenticationToken(decodedToken).grant.userId?.let(LockKey::User))

        lockManager.withLock(lockKeys) {
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
        val grant = refreshToken.grant
        if (grant.clientId != client.id) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.mismatching_client", "description.token.mismatching_client")
        }
        // A grant is the audience's it was opened for, and that is what revoking a consent reaches it by:
        // tokens issued for an audience its client has since moved to would escape the consent they rest on.
        if (grant.audienceId != client.audience.id) {
            throw oauth2ExceptionOf(
                INVALID_GRANT,
                "grant.mismatching_audience",
                "description.grant.mismatching_audience",
                "grantAudienceId" to grant.audienceId,
                "clientId" to client.id,
                "clientAudienceId" to client.audience.id
            )
        }

        dpopManager.checkProofMatchesBinding(refreshToken, dpopJkt)

        // For user grants, verify the account is one this server finished creating and the consent has not
        // been revoked (checked at audience level). A client-credentials grant carries no user.
        if (grant.userId != null) {
            checkPromotedOrInvalidGrant(grant.userId)
            consentManager.findActiveConsentByAudienceOrNull(grant.userId, client.audience.id)
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "grant.consent_revoked", "description.grant.consent_revoked")
        }

        // Use the DPoP jkt from the proof, or carry forward the existing binding
        val effectiveDpopJkt = dpopJkt ?: refreshToken.dpopJkt

        val accessToken = accessTokenGenerator.generateAccessToken(
            grant = grant,
            audience = client.audience,
            grantType = GrantType.REFRESH_TOKEN,
            dpopJkt = effectiveDpopJkt
        )
        val refreshedRefreshToken = if (shouldRefreshToken(refreshToken, accessToken)) {
            refreshTokenGenerator.generateRefreshToken(
                grant = grant,
                audience = client.audience,
                grantType = GrantType.REFRESH_TOKEN,
                dpopJkt = effectiveDpopJkt
            )
        } else null
        // A refresh reissues the identity because OpenID Connect Core §12.2 lets it, and a client holding
        // a refreshed access token with a stale id token has no way to learn the subject changed. Whether
        // this grant is owed one is the generator's to answer, so that the authorization code cannot
        // answer it differently.
        val idToken = idTokenGenerator.generateIdToken(
            grant = grant,
            audienceId = client.audience.id,
            accessToken = accessToken,
            grantType = GrantType.REFRESH_TOKEN
        )

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
            throw oauth2ExceptionOf(INVALID_GRANT, "grant.invalid_user", "description.grant.invalid_user")
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
     * Decode the [encodedToken] issued to [client], and revoke the grant it was issued under.
     *
     * Per RFC 7009, this method does not throw if the token is invalid, expired, already revoked, or not found.
     * Only a grant opened for [client] is revoked.
     *
     * The grant is revoked whichever token is given — an access token as surely as a refresh token, as RFC
     * 7009 §2.1 lets a server do — and so is every grant a token exchange opened from one of its tokens. See
     * `docs/design/grants.md`.
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
        if (token.grant.clientId != client.id) return

        grantManager.revokeGrant(token.grant, GrantRevokedBy.CLIENT, revokedById = null)
    }

    /**
     * Introspect the [encodedToken] and return the stored [AuthenticationToken] if the token is active,
     * or `null` if the token is invalid, expired, issued under a grant that is revoked or past its lifetime,
     * not owned by [client], or not of [type].
     *
     * A null [type] accepts an access token and a refresh token alike.
     */
    suspend fun introspectToken(
        client: Client,
        encodedToken: String,
        type: AuthenticationTokenType?
    ): AuthenticationToken? {
        val decodedToken = decodeAndVerifyAccessOrRefreshTokenOrNull(encodedToken) ?: return null

        val token = try {
            getAuthenticationToken(decodedToken)
        } catch (_: OAuth2Exception) {
            return null
        }
        if (type != null && token.type != type) return null
        if (token.grant.clientId != client.id) return null
        return token
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
     * Return the information we stored about the [decodedToken] when we issued it, with the grant it was
     * issued under.
     *
     * Throws an [OAuth2Exception] carrying [INVALID_GRANT] if:
     * - `token.invalid_token_id`: the identifier of the token cannot be decoded, the token cannot be found in
     *   the database despite being signed with our signature — which is what a token whose grant was
     *   collected is — or its subject is not the one its grant names.
     * - `grant.revoked`: the grant the token was issued under has been revoked.
     * - `grant.expired`: the grant the token was issued under is past its lifetime. Only a refresh
     *   token reaches it: every other token states an expiry no later than its grant's.
     * - the token is an act-as token (RFC 8693) whose actor grant is no longer live, as
     *   [ActorGrantValidator.validateActorGrant] says.
     */
    suspend fun getAuthenticationToken(decodedToken: DecodedJwt): AuthenticationToken {
        val id = try {
            UUID.fromString(decodedToken.id)
        } catch (_: IllegalArgumentException) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.invalid_token_id")
        }
        val token = findById(id)

        // For client credentials grants, subject is the client ID
        // For user grants, subject is the user ID
        val expectedSubject = token?.grant?.let { it.userId?.toString() ?: it.clientId }

        return when {
            token == null -> throw oauth2ExceptionOf(INVALID_GRANT, "token.invalid_token_id")
            decodedToken.subject != expectedSubject -> throw oauth2ExceptionOf(
                INVALID_GRANT, "token.invalid_token_id"
            )

            else -> {
                checkLive(token.grant)
                actorGrantValidator.validateActorGrant(token.grant)
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

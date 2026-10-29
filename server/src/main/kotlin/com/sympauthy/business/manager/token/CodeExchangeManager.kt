package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.withRefusalAs
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.auth.oauth2.AuthorizationCodeManager
import com.sympauthy.business.manager.auth.oauth2.PkceManager
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.token.IssuedTokens
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.user.claim.ClaimAclContext
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Exchanges an authorization code for the tokens of the end-user it was issued for, the `authorization_code`
 * grant. See `docs/design/oauth2-authorization.md`.
 */
@Singleton
open class CodeExchangeManager(
    @Inject private val authorizationCodeManager: AuthorizationCodeManager,
    @Inject private val pkceManager: PkceManager,
    @Inject private val grantManager: GrantManager,
    @Inject private val lockManager: LockManager,
    @Inject private val accessTokenGenerator: AccessTokenGenerator,
    @Inject private val refreshTokenGenerator: RefreshTokenGenerator,
    @Inject private val idTokenGenerator: IdTokenGenerator,
    @Inject private val claimAclManager: ClaimAclManager
) {

    /**
     * Exchange the code [request] carries for the tokens of the end-user it was issued for, checking its
     * verifier against the challenge the code carries, and bind them to [dpopJkt] if there is one.
     *
     * Throws whatever [AuthorizationCodeManager.checkCanExchange] throws, then the failure of the PKCE
     * verification as `invalid_grant`, then whatever [exchangeCode] throws.
     */
    suspend fun exchange(
        client: Client,
        request: TokenRequest.AuthorizationCode,
        dpopJkt: String?
    ): IssuedTokens {
        val (code, grant) = authorizationCodeManager.checkCanExchange(
            authorizationCodeManager.findByCodeOrNull(request.code), client
        )
        withRefusalAs(INVALID_GRANT) {
            pkceManager.verifyCodeVerifier(
                codeVerifier = request.codeVerifier,
                codeChallenge = code.codeChallenge,
                codeChallengeMethod = code.codeChallengeMethod
            )
        }
        return exchangeCode(code, grant, client, dpopJkt)
    }

    /**
     * Exchange [code] for the tokens of the user its [grant] was opened for, as [exchangeCodeOrNull] does.
     *
     * Throws an `OAuth2Exception` carrying `invalid_grant` and `code.already_exchanged` where a client
     * already exchanged [code], once the grant it names is revoked.
     */
    internal suspend fun exchangeCode(
        code: AuthorizationCode,
        grant: Grant,
        client: Client,
        dpopJkt: String? = null
    ): IssuedTokens {
        return exchangeCodeOrNull(code, grant, client, dpopJkt)
            ?: throw oauth2ExceptionOf(INVALID_GRANT, "code.already_exchanged", "description.oauth2.replay")
    }

    /**
     * Mark [code] as exchanged, and generate the tokens it is exchanged for under the grant it names. [code]
     * and [grant] are what [AuthorizationCodeManager.checkCanExchange] answered: the grant names the account
     * whose lock is taken, and is read again once the lock is held.
     *
     * Throws `code.missing_user`, internal, where [grant] names no user: an authorization opens its grant for
     * the person it signed in, so one without is this server's failure. Throws what
     * [GrantManager.checkUserPromoted] throws where that user is one no sign-up finished.
     *
     * Always generates an access token. An id token is generated where the grant carries the `openid`
     * scope, repeating the `nonce` [code] carries, and a refresh token where the [client] supports the
     * [GrantType.REFRESH_TOKEN] grant type. A grant that is no longer live, as [GrantManager.checkLive] says,
     * issues nothing and leaves the code unspent.
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
    internal open suspend fun exchangeCodeOrNull(
        code: AuthorizationCode,
        grant: Grant,
        client: Client,
        dpopJkt: String? = null
    ): IssuedTokens? {
        val userId = grant.userId
            ?: throw internalBusinessExceptionOf("code.missing_user", "grantId" to grant.id.toString())
        grantManager.checkUserPromoted(grant)

        return lockManager.withLock(listOf(LockKey.User(userId))) {
            val liveGrant = authorizationCodeManager.fetchGrant(code)
            if (!authorizationCodeManager.markExchanged(code)) {
                grantManager.revokeGrant(liveGrant, GrantRevokedBy.AUTHORIZATION_CODE_REPLAYED, revokedById = null)
                return@withLock null
            }
            grantManager.checkLive(liveGrant)
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
    ): IssuedTokens = coroutineScope {
        // Built once for the response: the id token is told what the access token beside it may be told.
        val doors = claimAclManager.getTokenDoors(ClaimAclContext.of(grant, client, dpopJkt))
        val deferredAccessToken = async {
            accessTokenGenerator.generateAccessToken(
                grant = grant,
                audience = client.audience,
                doors = doors,
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
                audience = client.audience,
                accessToken = accessToken,
                doors = doors,
                nonce = nonce
            )
        }

        IssuedTokens(
            accessToken = accessToken,
            refreshToken = deferredRefreshToken?.await(),
            idToken = deferredIdToken.await()
        )
    }
}

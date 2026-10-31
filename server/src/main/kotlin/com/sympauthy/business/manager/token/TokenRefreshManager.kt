package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.withRefusalAs
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.auth.oauth2.DpopManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.IssuedTokens
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.supervisorScope

/**
 * Issues new tokens under the grant a refresh token was issued under, the `refresh_token` grant. See
 * `docs/design/grants.md`.
 */
@Singleton
open class TokenRefreshManager(
    @Inject private val jwtManager: JwtManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val grantManager: GrantManager,
    @Inject private val consentManager: ConsentManager,
    @Inject private val dpopManager: DpopManager,
    @Inject private val lockManager: LockManager,
    @Inject private val accessTokenGenerator: AccessTokenGenerator,
    @Inject private val refreshTokenGenerator: RefreshTokenGenerator,
    @Inject private val idTokenGenerator: IdTokenGenerator,
    @Inject private val claimAclManager: ClaimAclManager,
    @Inject private val customSpanTracer: CustomSpanTracer
) {

    /**
     * Decode and verify the refresh token [request] carries, and issue a new access token under its grant.
     *
     * Additionally, a new refresh token may be issued if the refresh token expires
     * before the expiration of the new access token, and a new id token is issued when the grant the
     * refresh token came from carried the `openid` scope.
     *
     * Throws an `OAuth2Exception` carrying `invalid_grant` if the refresh token validation fails:
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
     * Throws an `OAuth2Exception` carrying `invalid_dpop_proof` where the token is bound to a key and
     * [dpopJkt] is absent (`dpop.missing_header`) or names a different one (`dpop.mismatching_key`).
     *
     * Every check is made, and every token issued, holding the lock over the account the grant belongs to,
     * which every revocation of that account's grants takes too. A revocation racing this refresh either
     * commits first, and is read here as the refusal it is, or waits for these tokens to be committed and
     * finds the grant live through them.
     */
    @Transactional
    open suspend fun refresh(
        client: Client,
        request: TokenRequest.RefreshToken,
        dpopJkt: String? = null
    ): IssuedTokens = supervisorScope {
        val decodedToken = withRefusalAs(INVALID_GRANT) {
            jwtManager.decodeAndVerify(REFRESH_KEY, request.refreshToken)
        }
        val lockKeys = listOfNotNull(tokenManager.getAuthenticationToken(decodedToken).grant.userId?.let(LockKey::User))

        lockManager.withLock(lockKeys) {
            refreshHeldToken(client, decodedToken, dpopJkt)
        }
    }

    /**
     * The body of [refresh], run once the lock over the account [decodedToken] belongs to is held.
     *
     * The token is read again here rather than handed in, because the read made to find the account came
     * before the wait: a revocation committed during it is only seen by a read made after.
     *
     * The request is marked as acting on the interactive flow session the token's grant came from, where one did.
     */
    internal suspend fun refreshHeldToken(
        client: Client,
        decodedToken: DecodedJwt,
        dpopJkt: String?
    ): IssuedTokens {
        val refreshToken = tokenManager.getAuthenticationToken(decodedToken)
        val grant = refreshToken.grant
        grant.sessionId?.let { customSpanTracer.markFlowSession(it, grant.sessionStartedBy) }
        grantManager.checkOpenedFor(grant, client)

        dpopManager.checkProofMatchesBinding(refreshToken, dpopJkt)

        // For user grants, verify the account is one this server finished creating and the consent has not
        // been revoked (checked at audience level). A client-credentials grant carries no user.
        if (grant.userId != null) {
            grantManager.checkUserPromoted(grant)
            consentManager.findActiveConsentByAudienceOrNull(grant.userId, client.audience.id)
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "grant.consent_revoked", "description.grant.consent_revoked")
        }

        // Use the DPoP jkt from the proof, or carry forward the existing binding
        val effectiveDpopJkt = dpopJkt ?: refreshToken.dpopJkt
        val doors = claimAclManager.getTokenDoors(ClaimAclContext.of(grant, client, effectiveDpopJkt))

        val accessToken = accessTokenGenerator.generateAccessToken(
            grant = grant,
            audience = client.audience,
            doors = doors,
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
            audience = client.audience,
            accessToken = accessToken,
            doors = doors,
            grantType = GrantType.REFRESH_TOKEN
        )

        return IssuedTokens(
            accessToken = accessToken,
            refreshToken = refreshedRefreshToken,
            idToken = idToken
        )
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
}

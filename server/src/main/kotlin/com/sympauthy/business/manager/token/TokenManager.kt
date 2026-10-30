package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.auth.ActorGrantValidator
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.jwt.JwtManager.Companion.ACCESS_KEY
import com.sympauthy.business.manager.jwt.JwtManager.Companion.REFRESH_KEY
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.grant.GrantOpenedForResult
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.token.AuthenticationTokenType
import com.sympauthy.data.repository.AuthenticationTokenRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.*

/**
 * A token once this server issued it: reading it back, introspecting it, and revoking the grant it was issued
 * under. Issuing one is the use case of the grant it is issued for.
 */
@Singleton
open class TokenManager(
    @Inject private val jwtManager: JwtManager,
    @Inject private val actorGrantValidator: ActorGrantValidator,
    @Inject private val grantManager: GrantManager,
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
     * not opened for [client] and the audience it belongs to now, or not of [type].
     *
     * A null [type] accepts an access token and a refresh token alike.
     *
     * A token issued for another audience than the one [client] belongs to is answered as inactive rather than
     * described: what an answer said of its audience and of the person's claims would be told to a client
     * outside that audience.
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
        if (grantManager.verifyOpenedFor(token.grant, client) is GrantOpenedForResult.Refused) return null
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
                grantManager.checkLive(token.grant)
                actorGrantValidator.validateActorGrant(token.grant)
                token
            }
        }
    }
}

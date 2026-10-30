package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.auth.toOAuth2Exception
import com.sympauthy.business.mapper.AuthorizationCodeMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantOpenedForResult
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Manager in charge of the authorization code a client exchanges for the access, refresh and id tokens.
 *
 * A code lives for `auth.authorization-code.expiration` from when it is handed out, whatever is left of the
 * interactive flow session that issued it, and is collected once that has passed, exchanged or not. See
 * `docs/design/oauth2-authorization.md`.
 */
@Singleton
class AuthorizationCodeManager(
    @Inject private val authorizeCodeRepository: AuthorizationCodeRepository,
    @Inject private val authorizationCodeMapper: AuthorizationCodeMapper,
    @Inject private val grantManager: GrantManager,
    @Inject private val randomGenerator: RandomGenerator,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    /**
     * Generate the authorization code a client exchanges for the tokens of the authorization request [oauth2],
     * in the transaction completing its interactive flow session, naming the [grant] that authorization
     * opened. The code carries the PKCE challenge and the `nonce` of [oauth2], which are what its exchange
     * reads instead of the session.
     *
     * The grant is written in the same transaction, just before, and holds this code alone: a session opens
     * one grant, so a completion that lost the race to a concurrent one is refused at the grant and never
     * reaches here. A refusal of the write travels on as this server's own failure, because nothing the client
     * sent would have prevented it.
     *
     * Throws `code.missing_code_challenge`, internal, where [oauth2] holds no PKCE challenge: an authorization
     * request without one is refused before its session starts, so one completing is this server's failure.
     */
    suspend fun generateCode(
        oauth2: InteractiveFlowSessionOAuth2,
        grant: Grant
    ): AuthorizationCode {
        val codeChallenge = oauth2.codeChallenge
        val codeChallengeMethod = oauth2.codeChallengeMethod
        if (codeChallenge == null || codeChallengeMethod == null) {
            throw internalBusinessExceptionOf("code.missing_code_challenge", "sessionId" to oauth2.sessionId.toString())
        }
        val creationDate = LocalDateTime.now()
        val entity = AuthorizationCodeEntity(
            grantId = grant.id,
            // To avoid encoded characters in the returned code.
            code = randomGenerator.generateAndEncodeToHex(CODE_LENGTH_IN_BYTES),
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod.value,
            nonce = oauth2.nonce,
            creationDate = creationDate,
            expirationDate = creationDate.plus(uncheckedAuthConfig.orThrow().authorizationCode.expiration)
        )
        return authorizeCodeRepository.save(entity)
            .let(authorizationCodeMapper::toAuthorizationCode)
    }

    /**
     * Return the [AuthorizationCode] whose value is [code], null where this server holds none — one it never
     * issued, or one collected since it expired.
     */
    suspend fun findByCodeOrNull(code: String): AuthorizationCode? {
        return authorizeCodeRepository.findByCode(code)
            ?.let(authorizationCodeMapper::toAuthorizationCode)
    }

    /**
     * The authorization code [session] completed with.
     *
     * Throws `code.missing`, internal, where it holds none: a session completing an authorization request is
     * given its code in the transaction completing it, so one without is this server's failure.
     */
    suspend fun fetchCode(session: InteractiveFlowSession): AuthorizationCode {
        return fetchCodeOrNull(session)
            ?: throw internalBusinessExceptionOf("code.missing", "sessionId" to session.id.toString())
    }

    /**
     * The authorization code [session] completed with, issued under the grant it opened, or null where it
     * holds none — a code is collected once it has expired, so one read after that finds it gone.
     */
    suspend fun fetchCodeOrNull(session: InteractiveFlowSession): AuthorizationCode? {
        return authorizeCodeRepository.findBySessionId(session.id)
            ?.let(authorizationCodeMapper::toAuthorizationCode)
    }

    /**
     * Either return the [code] the caller found together with the grant it names, if [client] may exchange
     * it, or throw an `OAuth2Exception` carrying `invalid_grant`:
     * - `token.expired` where [code] is null, which is a code this server does not hold, where it has
     *   expired, or where its grant is gone, as [fetchGrant] says.
     * - `token.mismatching_client` or `grant.mismatching_audience`, as [GrantManager.verifyOpenedFor] says.
     *
     * Nothing here spends the code, and nothing it refuses revokes anything. Whether the grant is still live
     * is read again where the code is spent, once the lock its revocations take is held.
     */
    suspend fun checkCanExchange(code: AuthorizationCode?, client: Client): Pair<AuthorizationCode, Grant> {
        if (code == null || code.expired) {
            throw oauth2ExceptionOf(INVALID_GRANT, "token.expired", "description.oauth2.expired")
        }
        val grant = fetchGrant(code)
        val openedFor = grantManager.verifyOpenedFor(grant, client)
        if (openedFor is GrantOpenedForResult.Refused) {
            throw openedFor.toOAuth2Exception()
        }
        return code to grant
    }

    /**
     * The grant [code] names.
     *
     * Throws an `OAuth2Exception` carrying `invalid_grant` and `token.expired` where the grant is gone. A grant
     * is kept as long as its code is, so one gone is a code collected since it was read: it had expired, and is
     * refused as one.
     */
    suspend fun fetchGrant(code: AuthorizationCode): Grant {
        return grantManager.findByIdOrNull(code.grantId)
            ?: throw oauth2ExceptionOf(INVALID_GRANT, "token.expired", "description.oauth2.expired")
    }

    /**
     * Mark [code] as exchanged, and answer whether this call is the one that marked it: false where a client
     * already exchanged it, or where it has been collected since it was read.
     *
     * Of two calls racing over one code, exactly one answers true. The second waits on the row the first
     * wrote until the transaction holding it ends, so a caller marking the code inside the transaction
     * that issues its tokens has the second caller find those tokens committed.
     */
    suspend fun markExchanged(code: AuthorizationCode): Boolean {
        return authorizeCodeRepository.updateExchangeDate(
            grantId = code.grantId,
            exchangeDate = LocalDateTime.now()
        ) == 1
    }

    companion object {
        /**
         * The random bytes an authorization code is made of: 256 bits, past the 2^-160 chance of guessing one
         * that RFC 6749 §10.10 recommends.
         */
        const val CODE_LENGTH_IN_BYTES = 32
    }
}

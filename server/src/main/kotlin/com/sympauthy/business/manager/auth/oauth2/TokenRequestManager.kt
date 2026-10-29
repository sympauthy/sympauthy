package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.api.exception.toOAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.ClientScopeGrantingManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_SCOPE
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNAUTHORIZED_CLIENT
import com.sympauthy.exception.toOutcome
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.TokenMetrics
import com.sympauthy.monitoring.telemetry.measureOutcome
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Issues the tokens a request to the token endpoint asks for, once its client has authenticated, one method per
 * grant type.
 *
 * Each request is recorded on `sympauthy.token.request.duration` under its grant type, its client and the scheme
 * its tokens are bound to, with how it ended.
 */
@Singleton
class TokenRequestManager(
    @Inject private val authorizationCodeManager: AuthorizationCodeManager,
    @Inject private val pkceManager: PkceManager,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val clientScopeGrantingManager: ClientScopeGrantingManager,
    @Inject private val tokenManager: TokenManager,
    @Inject private val tokenExchangeManager: TokenExchangeManager,
    @Inject private val tokenMetrics: TokenMetrics
) {

    /**
     * Exchange [code] for the tokens of the person it was issued for, checking [codeVerifier] against the
     * challenge the code carries, and bind them to [dpopJkt] if there is one.
     *
     * Throws an `OAuth2Exception` carrying `unauthorized_client` and `token.unauthorized_grant_type` where
     * [client] does not support the grant type, then one carrying `invalid_request` and `token.missing_param`
     * where [code] is missing, whatever [AuthorizationCodeManager.checkCanExchange] and
     * [TokenManager.generateTokensForUser] throw, and the failure of the PKCE verification as `invalid_grant`.
     */
    suspend fun exchangeAuthorizationCode(
        client: Client,
        code: String?,
        codeVerifier: String?,
        dpopJkt: String?
    ): GenerateTokenResult = record(GrantType.AUTHORIZATION_CODE, client, dpopJkt) {
        val requiredCode = getRequiredParam(code, CODE_PARAM)
        val (authorizationCode, grant) = authorizationCodeManager.checkCanExchange(
            authorizationCodeManager.findByCodeOrNull(requiredCode), client
        )
        try {
            pkceManager.verifyCodeVerifier(
                codeVerifier = codeVerifier,
                codeChallenge = authorizationCode.codeChallenge,
                codeChallengeMethod = authorizationCode.codeChallengeMethod
            )
        } catch (e: BusinessException) {
            throw e.toOAuth2Exception(INVALID_GRANT)
        }
        tokenManager.generateTokensForUser(authorizationCode, grant, client, dpopJkt = dpopJkt)
    }

    /**
     * Issue new tokens under the grant [encodedRefreshToken] was issued under, bound to [dpopJkt] if there is
     * one.
     *
     * Throws an `OAuth2Exception` carrying `unauthorized_client` and `token.unauthorized_grant_type` where
     * [client] does not support the grant type, then one carrying `invalid_request` and `token.missing_param`
     * where [encodedRefreshToken] is missing, and whatever [TokenManager.refreshToken] throws.
     */
    suspend fun refreshTokens(
        client: Client,
        encodedRefreshToken: String?,
        dpopJkt: String?
    ): GenerateTokenResult = record(GrantType.REFRESH_TOKEN, client, dpopJkt) {
        val requiredRefreshToken = getRequiredParam(encodedRefreshToken, REFRESH_TOKEN_PARAM)
        tokenManager.refreshToken(client, requiredRefreshToken, dpopJkt = dpopJkt)
    }

    /**
     * Issue [client] an access token of its own for the client scopes [scope] asks for, bound to [dpopJkt] if
     * there is one.
     *
     * Throws an `OAuth2Exception` carrying `unauthorized_client` and `token.unauthorized_grant_type` where
     * [client] does not support the grant type, and one carrying `invalid_scope` and the code of the refusal
     * where a scope it asked for is refused.
     */
    suspend fun generateClientToken(
        client: Client,
        scope: String?,
        dpopJkt: String?
    ): EncodedAuthenticationToken = record(GrantType.CLIENT_CREDENTIALS, client, dpopJkt) {
        // Every BusinessException this call throws refuses a scope the client asked for, so each one is
        // invalid_scope. A server failure thrown as one would reach the client as invalid_scope too; a scopes
        // configuration that failed to load throws a LocalizedException instead, and still answers 500.
        val requestedScopes = try {
            scopeManager.parseRequestedClientScopes(client, scope)
        } catch (e: BusinessException) {
            throw e.toOAuth2Exception(INVALID_SCOPE)
        }
        val grantResult = clientScopeGrantingManager.grantClientScopes(client, requestedScopes)
        tokenManager.generateAccessTokenForClient(
            client = client,
            clientScopes = grantResult.grantedScopes.map { it.scope },
            dpopJkt = dpopJkt
        )
    }

    /**
     * Exchange [subjectToken] for an access token letting [client] act as [requestedSubject], as
     * [TokenExchangeManager.exchangeForActAsToken] does, bound to [dpopJkt] if there is one.
     *
     * Throws an `OAuth2Exception` carrying `unauthorized_client` and `token.unauthorized_grant_type` where
     * [client] does not support the grant type, then one carrying `invalid_request` and `token.missing_param`
     * where [subjectToken], [subjectTokenType] or [requestedSubject] is missing, and one carrying
     * `invalid_request` and `token_exchange.unsupported_requested_token_type` where [requestedTokenType] names
     * anything but an access token. Then whatever [TokenExchangeManager.exchangeForActAsToken] throws.
     */
    @Suppress("LongParameterList")
    suspend fun exchangeForActAsToken(
        client: Client,
        subjectToken: String?,
        subjectTokenType: String?,
        requestedSubject: String?,
        requestedTokenType: String?,
        resource: String?,
        audience: String?,
        dpopJkt: String?
    ): EncodedAuthenticationToken = record(GrantType.TOKEN_EXCHANGE, client, dpopJkt) {
        val requiredSubjectToken = getRequiredParam(subjectToken, SUBJECT_TOKEN_PARAM)
        val requiredSubjectTokenType = getRequiredParam(subjectTokenType, SUBJECT_TOKEN_TYPE_PARAM)
        val requiredRequestedSubject = getRequiredParam(requestedSubject, REQUESTED_SUBJECT_PARAM)
        // requested_token_type is optional, but when present we only support issuing access tokens.
        if (requestedTokenType != null && requestedTokenType != TokenExchangeManager.ACCESS_TOKEN_TYPE) {
            throw oauth2ExceptionOf(
                INVALID_REQUEST, "token_exchange.unsupported_requested_token_type", "type" to requestedTokenType
            )
        }
        tokenExchangeManager.exchangeForActAsToken(
            actingClient = client,
            subjectToken = requiredSubjectToken,
            subjectTokenType = requiredSubjectTokenType,
            requestedSubject = requiredRequestedSubject,
            resource = resource,
            audience = audience,
            dpopJkt = dpopJkt
        )
    }

    /**
     * Run [block] as a request by [client] for [grantType], refused first where [client] does not support it,
     * and record how it ended.
     */
    internal suspend fun <T> record(
        grantType: GrantType,
        client: Client,
        dpopJkt: String?,
        block: suspend () -> T
    ): T {
        val scheme = if (dpopJkt != null) DPOP_SCHEME else BEARER_SCHEME
        return measureOutcome(
            onSuccess = { elapsed, _ ->
                tokenMetrics.recordRequest(grantType.wireName, scheme, client.id, elapsed, Outcome.Success)
            },
            onFailure = { elapsed, failure ->
                tokenMetrics.recordRequest(grantType.wireName, scheme, client.id, elapsed, failure.toOutcome())
            }
        ) {
            checkClientSupportsGrantType(client, grantType)
            block()
        }
    }

    /**
     * [value], the request parameter named [param].
     *
     * Throws an `OAuth2Exception` carrying `invalid_request` and `token.missing_param`, naming [param], where
     * [value] is missing or blank.
     */
    internal fun getRequiredParam(value: String?, param: String): String {
        if (value.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "token.missing_param", "param" to param)
        }
        return value
    }

    internal fun checkClientSupportsGrantType(client: Client, grantType: GrantType) {
        if (!client.supportsGrantType(grantType)) {
            throw oauth2ExceptionOf(
                UNAUTHORIZED_CLIENT,
                "token.unauthorized_grant_type",
                "description.token.unauthorized_grant_type",
                "grantType" to grantType.wireName
            )
        }
    }

    companion object {
        const val CODE_PARAM = "code"
        const val REFRESH_TOKEN_PARAM = "refresh_token"
        const val SUBJECT_TOKEN_PARAM = "subject_token"
        const val SUBJECT_TOKEN_TYPE_PARAM = "subject_token_type"
        const val REQUESTED_SUBJECT_PARAM = "requested_subject"

        /**
         * The scheme a request is recorded under where its tokens are bound to a key it proved it holds.
         */
        private const val DPOP_SCHEME = "dpop"
        private const val BEARER_SCHEME = "bearer"
    }
}

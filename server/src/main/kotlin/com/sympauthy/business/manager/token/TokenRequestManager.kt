package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.UNAUTHORIZED_CLIENT
import com.sympauthy.business.model.token.IssuedTokens
import com.sympauthy.business.model.token.TokenIssuance
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.exception.toOutcome
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.TokenMetrics
import com.sympauthy.monitoring.telemetry.measureOutcome
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Issues the tokens a request to the token endpoint asks for, once its client has authenticated, through the
 * use case of the grant it asks for.
 *
 * Each request is recorded on `sympauthy.token.request.duration` under its grant type, its client and the scheme
 * its tokens are bound to, with how it ended.
 */
@Singleton
class TokenRequestManager(
    @Inject private val codeExchangeManager: CodeExchangeManager,
    @Inject private val tokenRefreshManager: TokenRefreshManager,
    @Inject private val clientCredentialsManager: ClientCredentialsManager,
    @Inject private val tokenExchangeManager: TokenExchangeManager,
    @Inject private val tokenMetrics: TokenMetrics
) {

    /**
     * Issue the tokens a request by [client] for [grantType] asks for, bound to [dpopJkt] if there is one, once
     * [readRequest] has read the request into that grant.
     *
     * Throws an `OAuth2Exception` carrying `unauthorized_client` and `token.unauthorized_grant_type` where
     * [client] does not support [grantType], before [readRequest] is called. Then whatever [readRequest] throws,
     * `token.mismatching_grant_type`, internal, where it read a request for another grant than [grantType], and
     * whatever the use case of the grant throws.
     *
     * The request is checked against [grantType] because that is the grant the client was checked for and the
     * request is recorded under: a request for another one would be served to a client never allowed it.
     *
     * A request [readRequest] refuses is recorded like any other, under [grantType] and [client]: a client
     * sending malformed requests is one an operator is looking for.
     */
    suspend fun issueTokens(
        client: Client,
        grantType: GrantType,
        dpopJkt: String?,
        readRequest: () -> TokenRequest
    ): TokenIssuance = record(grantType, client, dpopJkt) {
        checkClientSupportsGrantType(client, grantType)
        val request = readRequest()
        if (request.grantType != grantType) {
            throw internalBusinessExceptionOf(
                "token.mismatching_grant_type",
                "grantType" to grantType.wireName,
                "requestGrantType" to request.grantType.wireName
            )
        }
        val tokens = when (request) {
            is TokenRequest.AuthorizationCode -> codeExchangeManager.exchange(client, request, dpopJkt)
            is TokenRequest.RefreshToken -> tokenRefreshManager.refresh(client, request, dpopJkt)
            is TokenRequest.ClientCredentials -> clientCredentialsManager.issue(client, request, dpopJkt)
            is TokenRequest.TokenExchange -> IssuedTokens(
                accessToken = tokenExchangeManager.exchangeForActAsToken(client, request, dpopJkt)
            )
        }
        TokenIssuance(request = request, tokens = tokens)
    }

    /**
     * Run [block] as a request by [client] for [grantType], and record how it ended.
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
            block()
        }
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

    private companion object {
        /**
         * The scheme a request is recorded under where its tokens are bound to a key it proved it holds.
         */
        const val DPOP_SCHEME = "dpop"
        const val BEARER_SCHEME = "bearer"
    }
}

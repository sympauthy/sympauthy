package com.sympauthy.business.manager.token

import com.sympauthy.api.exception.withRefusalAs
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.ClientScopeGrantingManager
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_SCOPE
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.business.model.token.IssuedTokens
import com.sympauthy.business.model.token.TokenRequest
import com.sympauthy.business.model.user.claim.ClaimAclContext
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Issues a client an access token of its own, the `client_credentials` grant.
 */
@Singleton
open class ClientCredentialsManager(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val clientScopeGrantingManager: ClientScopeGrantingManager,
    @Inject private val grantManager: GrantManager,
    @Inject private val accessTokenGenerator: AccessTokenGenerator,
    @Inject private val claimAclManager: ClaimAclManager
) {

    /**
     * Issue [client] an access token of its own for the client scopes [request] asks for, bound to [dpopJkt]
     * if there is one.
     *
     * Throws an `OAuth2Exception` carrying `invalid_scope` and the code of the refusal where a scope it asked
     * for is refused.
     */
    suspend fun issue(
        client: Client,
        request: TokenRequest.ClientCredentials,
        dpopJkt: String?
    ): IssuedTokens {
        val requestedScopes = withRefusalAs(INVALID_SCOPE) {
            scopeManager.parseRequestedClientScopes(client, request.scope)
        }
        val grantResult = clientScopeGrantingManager.grantClientScopes(client, requestedScopes)
        val accessToken = generateAccessToken(
            client = client,
            clientScopes = grantResult.grantedScopes.map { it.scope },
            dpopJkt = dpopJkt
        )
        return IssuedTokens(accessToken = accessToken)
    }

    /**
     * Open the grant a `client_credentials` request from [client] is answered under, holding the
     * [clientScopes] granted to it, and issue the access token it is answered with.
     *
     * Passing [dpopJkt] binds the token to that JWK thumbprint, and leaving it null issues a bearer token.
     */
    @Transactional
    internal open suspend fun generateAccessToken(
        client: Client,
        clientScopes: List<String>,
        dpopJkt: String? = null
    ): EncodedAuthenticationToken {
        val grant = grantManager.createClientCredentialsGrant(client.id, client.audience.id, clientScopes)
        return accessTokenGenerator.generateAccessToken(
            grant = grant,
            audience = client.audience,
            doors = claimAclManager.getTokenDoors(ClaimAclContext.of(grant, client, dpopJkt)),
            dpopJkt = dpopJkt
        )
    }
}

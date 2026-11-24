package com.sympauthy.business.token.manager

import com.sympauthy.api.exception.withRefusalAs
import com.sympauthy.business.claim.manager.ClaimAclManager
import com.sympauthy.business.claim.model.ClaimAclContext
import com.sympauthy.business.client.model.Client
import com.sympauthy.business.grant.manager.GrantManager
import com.sympauthy.business.oauth2.model.OAuth2ErrorCode.INVALID_SCOPE
import com.sympauthy.business.scope.granting.manager.ClientScopeGrantingManager
import com.sympauthy.business.scope.manager.ScopeManager
import com.sympauthy.business.token.model.EncodedAuthenticationToken
import com.sympauthy.business.token.model.IssuedTokens
import com.sympauthy.business.token.model.TokenRequest
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
     *
     * The grant and its token are written together. Committed alone, the grant is one no token and no code
     * names yet, which the collecting job may delete as finished before the token is written under it, and a
     * failure writing the token would leave a grant nothing was ever issued under.
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

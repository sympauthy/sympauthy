package com.sympauthy.api.controller.oauth2.util

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_GRANT
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.BasicAuthUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Utility class for OAuth2 controllers that require client authentication.
 *
 * It resolves and authenticates the client from the request, supporting two credential transmission methods:
 * - HTTP Basic Auth header (`Authorization: Basic <base64(client_id:client_secret)>`)
 * - Form body parameters (`client_id` / `client_secret`)
 *
 * This logic lives in a controller-layer utility rather than in a Micronaut
 * [io.micronaut.security.filters.AuthenticationFetcher] because [io.micronaut.security.filters.AuthenticationFetcher]
 * runs before the controller in the security filter chain, at a point where the request body is no longer accessible.
 * For `application/x-www-form-urlencoded` requests, Micronaut's Netty layer decodes the body into form parameters
 * during request processing, which causes [io.micronaut.http.HttpRequest.getBody] to return an empty
 * [java.util.Optional] in the fetcher. Moving the logic here allows Micronaut to bind body parameters correctly
 * via [io.micronaut.http.annotation.Part].
 */
@Singleton
class ClientAuthenticationUtil(
    @Inject private val clientManager: ClientManager
) {

    /**
     * Resolve and authenticate the client for grants that always require client credentials
     * (`client_credentials`, `refresh_token`).
     * Public clients are rejected by this method since they cannot authenticate.
     */
    suspend fun resolveClient(
        request: HttpRequest<*>,
        clientId: String?,
        clientSecret: String?
    ): Client {
        val headerCredentials = request.headers.authorization
            ?.flatMap(BasicAuthUtils::parseCredentials)
            ?.orElse(null)
        if (headerCredentials != null) {
            return clientManager.authenticateClientOrNull(headerCredentials.username, headerCredentials.password)
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")
        }
        if (clientId != null) {
            return clientManager.authenticateClientOrNull(clientId, clientSecret ?: "")
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")
        }
        throw oauth2ExceptionOf(INVALID_GRANT, "authentication.missing_credentials")
    }

    /**
     * Resolve the client for the `authorization_code` grant.
     *
     * - Confidential clients must authenticate with credentials (Basic Auth or form params).
     * - Public clients only need to provide `client_id` (no secret required); they rely on PKCE for security.
     */
    suspend fun resolveClientForAuthorizationCodeGrant(
        request: HttpRequest<*>,
        clientId: String?,
        clientSecret: String?
    ): Client {
        val headerCredentials = request.headers.authorization
            ?.flatMap(BasicAuthUtils::parseCredentials)
            ?.orElse(null)
        if (headerCredentials != null) {
            return clientManager.authenticateClientOrNull(headerCredentials.username, headerCredentials.password)
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")
        }
        if (clientId != null) {
            // If a secret is provided, authenticate normally
            if (!clientSecret.isNullOrBlank()) {
                return clientManager.authenticateClientOrNull(clientId, clientSecret)
                    ?: throw oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")
            }
            // No secret: only allow if the client is public
            return clientManager.findPublicClientByIdOrNull(clientId)
                ?: throw oauth2ExceptionOf(INVALID_GRANT, "authentication.wrong")
        }
        throw oauth2ExceptionOf(INVALID_GRANT, "authentication.missing_credentials")
    }
}

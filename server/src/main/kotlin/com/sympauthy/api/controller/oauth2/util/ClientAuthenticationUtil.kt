package com.sympauthy.api.controller.oauth2.util

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_CLIENT
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
 * Every refusal is `invalid_client`, RFC 6749 §5.2, and the one of credentials sent in the header also
 * carries the challenge §5.2 requires — see [OAuth2Exception.challenge].
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
     * (`client_credentials`).
     * Public clients are rejected by this method since they cannot authenticate.
     */
    suspend fun resolveClient(
        request: HttpRequest<*>,
        clientId: String?,
        clientSecret: String?
    ): Client {
        authenticateWithHeaderOrNull(request)?.let { return it }
        if (clientId != null) {
            return clientManager.authenticateClientOrNull(clientId, clientSecret)
                ?: throw oauth2ExceptionOf(INVALID_CLIENT, "authentication.wrong", "description.authentication.wrong")
        }
        throw oauth2ExceptionOf(
            INVALID_CLIENT,
            "authentication.missing_credentials",
            "description.authentication.missing_credentials"
        )
    }

    /**
     * Resolve the client for grants that support public clients (`authorization_code`, `refresh_token`).
     *
     * - Confidential clients must authenticate with credentials (Basic Auth or form params).
     * - Public clients only need to provide `client_id` (no secret required).
     */
    suspend fun resolveClientAllowingPublic(
        request: HttpRequest<*>,
        clientId: String?,
        clientSecret: String?
    ): Client {
        authenticateWithHeaderOrNull(request)?.let { return it }
        if (clientId != null) {
            // If a secret is provided, authenticate normally
            if (!clientSecret.isNullOrBlank()) {
                return clientManager.authenticateClientOrNull(clientId, clientSecret)
                    ?: throw oauth2ExceptionOf(
                        INVALID_CLIENT,
                        "authentication.wrong",
                        "description.authentication.wrong"
                    )
            }
            // No secret: only allow if the client is public
            return clientManager.findPublicClientByIdOrNull(clientId)
                ?: throw oauth2ExceptionOf(INVALID_CLIENT, "authentication.wrong", "description.authentication.wrong")
        }
        throw oauth2ExceptionOf(
            INVALID_CLIENT,
            "authentication.missing_credentials",
            "description.authentication.missing_credentials"
        )
    }

    /**
     * The client the Basic credentials of the `Authorization` header authenticate, or null where the
     * request carries none, in which case the body is read instead.
     *
     * Credentials there that do not authenticate are refused with [BASIC_CHALLENGE].
     */
    private suspend fun authenticateWithHeaderOrNull(request: HttpRequest<*>): Client? {
        val credentials = request.headers.authorization
            ?.flatMap(BasicAuthUtils::parseCredentials)
            ?.orElse(null)
            ?: return null
        return clientManager.authenticateClientOrNull(credentials.username, credentials.password)
            ?: throw OAuth2Exception(
                errorCode = INVALID_CLIENT,
                detailsId = "authentication.wrong",
                descriptionId = "description.authentication.wrong",
                challenge = BASIC_CHALLENGE
            )
    }

    companion object {
        /**
         * The challenge a client refused for the Basic credentials of its `Authorization` header is
         * answered with: the scheme it used, as RFC 6749 §5.2 requires.
         *
         * RFC 7617 §2 requires the realm, and the realm names the protection space within this server
         * the credentials belong to. Client credentials are accepted on the OAuth2 surface and nowhere
         * else, so the realm is that surface.
         */
        const val BASIC_CHALLENGE = """Basic realm="oauth2""""
    }
}

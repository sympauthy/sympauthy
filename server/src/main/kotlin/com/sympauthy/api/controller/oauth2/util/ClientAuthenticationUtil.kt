package com.sympauthy.api.controller.oauth2.util

import com.sympauthy.api.exception.OAuth2Exception
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
 * Every refusal is `invalid_client`, RFC 6749 §5.2. It carries [BASIC_CHALLENGE], and so answers `401`, wherever
 * no public client can be the one refused: on credentials sent in the header, and on every refusal of a client
 * that must be confidential. `docs/design/security.md` says why the rest are answered `400`.
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
     * Resolve and authenticate a client that must be confidential, from the Basic credentials of the
     * `Authorization` header or else from [clientId] and [clientSecret].
     *
     * A public client is refused, since it cannot authenticate. Every refusal carries [BASIC_CHALLENGE].
     */
    suspend fun resolveClient(
        request: HttpRequest<*>,
        clientId: String?,
        clientSecret: String?
    ): Client {
        authenticateWithHeaderOrNull(request)?.let { return it }
        if (clientId == null) {
            throw missingCredentialsException(challenged = true)
        }
        return clientManager.authenticateClientOrNull(clientId, clientSecret)
            ?: throw wrongCredentialsException(challenged = true)
    }

    /**
     * Resolve a client that may be public, from the Basic credentials of the `Authorization` header or else
     * from [clientId] and [clientSecret].
     *
     * A confidential client authenticates with its secret, and a public client sends [clientId] alone. A
     * refusal carries [BASIC_CHALLENGE] only where the client sent the header.
     */
    suspend fun resolveClientAllowingPublic(
        request: HttpRequest<*>,
        clientId: String?,
        clientSecret: String?
    ): Client {
        authenticateWithHeaderOrNull(request)?.let { return it }
        if (clientId == null) {
            throw missingCredentialsException(challenged = false)
        }
        val client = if (clientSecret.isNullOrBlank()) {
            clientManager.findPublicClientByIdOrNull(clientId)
        } else {
            clientManager.authenticateClientOrNull(clientId, clientSecret)
        }
        return client ?: throw wrongCredentialsException(challenged = false)
    }

    /**
     * The client the `Authorization` header authenticates, or null where it does not use the `Basic` scheme, in
     * which case the body is read instead.
     *
     * Credentials that do not decode, or do not authenticate, are refused with [BASIC_CHALLENGE].
     *
     * The scheme is matched regardless of case, and the credentials may follow it after more than one space, as
     * RFC 9110 §11.1 and §11.4 have it. The framework's parser accepts the scheme spelled one way and one space
     * after it, so it is handed the header written that way.
     */
    private suspend fun authenticateWithHeaderOrNull(request: HttpRequest<*>): Client? {
        val authorization = request.headers.authorization
            .orElse(null)
            ?.takeIf { it.startsWith(BASIC_SCHEME_PREFIX, ignoreCase = true) }
            ?: return null
        val credentials = BasicAuthUtils
            .parseCredentials(BASIC_SCHEME_PREFIX + authorization.substring(BASIC_SCHEME_PREFIX.length).trimStart(' '))
            .orElse(null)
            ?: throw wrongCredentialsException(challenged = true)
        return clientManager.authenticateClientOrNull(credentials.username, credentials.password)
            ?: throw wrongCredentialsException(challenged = true)
    }

    private fun wrongCredentialsException(challenged: Boolean) = OAuth2Exception(
        errorCode = INVALID_CLIENT,
        detailsId = "authentication.wrong",
        descriptionId = "description.authentication.wrong",
        challenge = if (challenged) BASIC_CHALLENGE else null
    )

    private fun missingCredentialsException(challenged: Boolean) = OAuth2Exception(
        errorCode = INVALID_CLIENT,
        detailsId = "authentication.missing_credentials",
        descriptionId = "description.authentication.missing_credentials",
        challenge = if (challenged) BASIC_CHALLENGE else null
    )

    companion object {
        /**
         * The `Basic` scheme and the space separating it from the credentials, as the framework's parser spells
         * them.
         */
        private const val BASIC_SCHEME_PREFIX = "Basic "

        /**
         * The challenge a refused client is answered with: the `Basic` scheme, the only one this server accepts
         * client credentials in, as RFC 6749 §5.2 requires.
         *
         * RFC 7617 §2 requires the realm: a `Basic` challenge without one is malformed. RFC 9110 §11.5 makes
         * the realm, together with the root URL of the server, a protection space — the resources one set of
         * credentials is good for. The root URL already says which server this is, so the realm only has to
         * tell the spaces within it apart, and naming the server again, by its issuer or by its product name,
         * would add nothing. Client credentials are accepted on the OAuth2 surface and nowhere else, so the
         * realm names that surface.
         *
         * Nothing branches on the value: no OAuth2 client reads it, and a browser only keys the credentials it
         * remembers on it and shows it in the prompt it may put up. Renaming it breaks no client.
         */
        const val BASIC_CHALLENGE = """Basic realm="oauth2""""
    }
}

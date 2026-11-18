package com.sympauthy.business.manager.client

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.model.client.Client
import io.micronaut.http.HttpStatus.BAD_REQUEST
import jakarta.inject.Singleton
import java.net.URI
import java.net.URISyntaxException

/**
 * Validates the URLs a request asks to send a person back to against what a [Client] declared: the OAuth2
 * `redirect_uri` of an authorization against its redirect URIs, and the `return_uri` of a flow a client or an
 * administrator started against its return URIs.
 *
 * The two lists are never read for each other, as `docs/design/security.md` has it.
 */
@Singleton
class ClientRedirectUriManager {

    /**
     * Parses [uncheckedRedirectUri] and checks it against [client]'s registered redirect URIs, returning
     * the validated [URI]. One that is missing, malformed or not registered throws a [BusinessException]
     * whose detail id says which of the three it was.
     *
     * The exception is not recoverable: the authorization the redirect URI arrived with fails, so the end-user
     * reaches the flow's error page rather than an unusable redirect URI.
     */
    fun parseRequestedRedirectUri(
        client: Client,
        uncheckedRedirectUri: String?
    ): URI {
        if (uncheckedRedirectUri.isNullOrBlank()) {
            throw BusinessException(
                recoverable = false,
                detailsId = "client.redirect_uri.missing",
                descriptionId = "description.client.redirect_uri.missing"
            )
        }
        val redirectURI = try {
            URI(uncheckedRedirectUri)
        } catch (e: URISyntaxException) {
            throw BusinessException(
                recoverable = false,
                detailsId = "client.redirect_uri.invalid",
                descriptionId = "description.client.redirect_uri.invalid",
                values = mapOf("redirect_uri" to uncheckedRedirectUri, "message" to (e.message ?: "")),
                throwable = e
            )
        }

        if (!matchesAllowedRedirectUri(uncheckedRedirectUri, client.allowedRedirectUris)) {
            throw BusinessException(
                recoverable = false,
                detailsId = "client.redirect_uri.not_allowed",
                descriptionId = "description.client.redirect_uri.not_allowed",
                values = mapOf("redirect_uri" to uncheckedRedirectUri)
            )
        }
        return redirectURI
    }

    /**
     * Parses [uncheckedReturnUri] and checks it against [client]'s return URIs, returning the validated [URI]:
     * [Client.returnUri] where [uncheckedReturnUri] is null or blank, and otherwise [uncheckedReturnUri] where
     * it is [Client.returnUri] or one of [Client.allowedReturnUris], compared as a redirect URI is.
     *
     * Throws a recoverable [BusinessException], since the URL is the request's own to correct:
     * - `client.return_uri.missing` where the request names none and the client declares no return URI.
     * - `client.return_uri.invalid` where the URL cannot be parsed.
     * - `client.return_uri.not_allowed` where the client declares neither as this URL.
     */
    fun parseRequestedReturnUri(
        client: Client,
        uncheckedReturnUri: String?
    ): URI {
        val returnUri = uncheckedReturnUri?.takeIf { it.isNotBlank() }
            ?: client.returnUri
            ?: throw recoverableBusinessExceptionOf(
                "client.return_uri.missing",
                "description.client.return_uri.missing",
                "clientId" to client.id
            )
        val parsedReturnUri = try {
            URI(returnUri)
        } catch (e: URISyntaxException) {
            throw BusinessException(
                recoverable = true,
                detailsId = "client.return_uri.invalid",
                descriptionId = "description.client.return_uri.invalid",
                recommendedStatus = BAD_REQUEST,
                values = mapOf("return_uri" to returnUri, "message" to (e.message ?: "")),
                throwable = e
            )
        }

        val allowedReturnUris = listOfNotNull(client.returnUri) + client.allowedReturnUris
        if (!matchesAllowedRedirectUri(returnUri, allowedReturnUris)) {
            throw recoverableBusinessExceptionOf(
                "client.return_uri.not_allowed",
                "description.client.return_uri.not_allowed",
                "return_uri" to returnUri
            )
        }
        return parsedReturnUri
    }

    /**
     * Returns [uncheckedReturnState], the value a client asks to have sent back with the end-user on returning
     * to it, or null where it is null or blank.
     *
     * Throws a recoverable [BusinessException] `client.return_state.too_long` where it is longer than
     * [RETURN_STATE_MAX_LENGTH] characters.
     *
     * The value is carried in the query of the redirect returning the end-user, and an address too long for the
     * client's own server or proxy to accept would strand the end-user on the way back.
     */
    fun parseRequestedReturnState(uncheckedReturnState: String?): String? {
        val returnState = uncheckedReturnState?.takeIf { it.isNotBlank() } ?: return null
        if (returnState.length > RETURN_STATE_MAX_LENGTH) {
            throw recoverableBusinessExceptionOf(
                "client.return_state.too_long",
                "description.client.return_state.too_long",
                "maxLength" to RETURN_STATE_MAX_LENGTH.toString()
            )
        }
        return returnState
    }

    /**
     * Check if [requestedUri] matches any of the [allowedUris] using exact string matching
     * per OAuth 2.1 (section 7.5.3), with loopback port flexibility per RFC 8252.
     *
     * Loopback flexibility: for redirect URIs using `127.0.0.1` or `::1` with `http`/`https`,
     * the port is ignored during matching. This does NOT apply to `localhost`.
     */
    internal fun matchesAllowedRedirectUri(requestedUri: String, allowedUris: List<String>): Boolean {
        // 1. Exact string match (OAuth 2.1 compliance)
        if (allowedUris.contains(requestedUri)) return true

        // 2. Loopback port flexibility (RFC 8252)
        val parsedRequested = try {
            URI(requestedUri)
        } catch (_: URISyntaxException) {
            return false
        }
        val requestedScheme = parsedRequested.scheme ?: return false
        if (requestedScheme != "http" && requestedScheme != "https") return false
        val requestedHost = parsedRequested.host ?: return false
        if (requestedHost != "127.0.0.1" && requestedHost != "[::1]") return false

        return allowedUris.any { allowedUri ->
            val parsedAllowed = try {
                URI(allowedUri)
            } catch (_: URISyntaxException) {
                return@any false
            }
            parsedAllowed.scheme == requestedScheme &&
                    parsedAllowed.host == requestedHost &&
                    parsedAllowed.path == parsedRequested.path &&
                    parsedAllowed.query == parsedRequested.query &&
                    parsedAllowed.fragment == parsedRequested.fragment
        }
    }

    companion object {
        /**
         * Number of characters a `return_state` may hold.
         */
        const val RETURN_STATE_MAX_LENGTH = 512
    }
}

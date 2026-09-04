package com.sympauthy.business.manager.client

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.client.Client
import jakarta.inject.Singleton
import java.net.URI
import java.net.URISyntaxException

/**
 * Validates redirect URIs against a [Client]'s registered redirect URIs — the OAuth2 `redirect_uri` of
 * the browser authorize flow as well as the `return_uri` / `cancel_uri` of the standalone client- and
 * admin-initiated flows (e.g. MFA enrollment).
 *
 * This is intentionally decoupled from any particular flow purpose: every consumer validates a
 * caller-supplied redirect URI the same way. They differ only in how a rejection should surface, which
 * the caller controls via the `recoverable` flag on [parseRequestedRedirectUri].
 */
@Singleton
class ClientRedirectUriManager {

    /**
     * Parses [uncheckedRedirectUri] and checks it against [client]'s registered redirect URIs, returning
     * the validated [URI]. One that is missing, malformed or not registered throws a [BusinessException]
     * whose detail id says which of the three it was.
     *
     * [recoverable] is the flag that exception carries, and it is how the rejection surfaces. The browser
     * authorize flow passes `false` — an unusable redirect URI is a hard error it cannot safely redirect
     * back from (HTTP 500). The client and admin APIs pass `true` — the caller sent a bad request
     * parameter (HTTP 400).
     */
    fun parseRequestedRedirectUri(
        client: Client,
        uncheckedRedirectUri: String?,
        recoverable: Boolean
    ): URI {
        if (uncheckedRedirectUri.isNullOrBlank()) {
            throw BusinessException(
                recoverable = recoverable,
                detailsId = "client.redirect_uri.missing",
                descriptionId = "description.client.redirect_uri.missing"
            )
        }
        val redirectURI = try {
            URI(uncheckedRedirectUri)
        } catch (e: URISyntaxException) {
            throw BusinessException(
                recoverable = recoverable,
                detailsId = "client.redirect_uri.invalid",
                descriptionId = "description.client.redirect_uri.invalid",
                values = mapOf("redirect_uri" to uncheckedRedirectUri, "message" to (e.message ?: "")),
                throwable = e
            )
        }

        if (!matchesAllowedRedirectUri(uncheckedRedirectUri, client.allowedRedirectUris)) {
            throw BusinessException(
                recoverable = recoverable,
                detailsId = "client.redirect_uri.not_allowed",
                descriptionId = "description.client.redirect_uri.not_allowed",
                values = mapOf("redirect_uri" to uncheckedRedirectUri)
            )
        }
        return redirectURI
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
}

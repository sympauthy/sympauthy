package com.sympauthy.business.model.oauth2

import com.sympauthy.util.wireName

/**
 * The kind of credential the authorization endpoint is asked to return, named by the `response_type`
 * parameter of an authorization request.
 *
 * This set is what the discovery document advertises under `response_types_supported`, and the
 * authorization endpoint dispatches on it exhaustively. A flow this server does not implement
 * therefore cannot be advertised: an entry added here does not compile until the endpoint has a
 * branch for it.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Authentication">Authentication</a>
 */
enum class ResponseType {

    /**
     * The authorization code flow: the endpoint hands back a code the client exchanges for tokens at
     * the token endpoint.
     */
    CODE;

    companion object {
        fun fromWireNameOrNull(wireName: String?): ResponseType? =
            entries.firstOrNull { it.wireName == wireName }
    }
}

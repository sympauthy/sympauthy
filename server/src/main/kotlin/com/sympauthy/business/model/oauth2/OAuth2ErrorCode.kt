package com.sympauthy.business.model.oauth2

import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpStatus.*

/**
 * List of predefined errors codes standardized by the OAuth2 authorization framework.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1">Error response</a>
 */
enum class OAuth2ErrorCode(
    val status: HttpStatus,
    val errorCode: String,
    val defaultDescriptionId: String
) {

    /**
     * The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than
     * once, or is otherwise malformed.
     */
    INVALID_REQUEST(
        BAD_REQUEST,
        "invalid_request",
        "description.oauth2.invalid"
    ),

    /**
     * The client is not authorized to request an authorization code using this method.
     */
    UNAUTHORIZED_CLIENT(
        BAD_REQUEST,
        "unauthorized_client",
        "description.oauth2.invalid"
    ),

    /**
     * The resource owner or authorization server denied the request.
     */
    ACCESS_DENIED(
        UNAUTHORIZED,
        "access_denied",
        "description.oauth2.invalid"
    ),

    /**
     * The authorization server does not support obtaining an authorization code using this method.
     */
    UNSUPPORTED_RESPONSE_TYPE(
        BAD_REQUEST,
        "unsupported_response_type",
        "description.oauth2.invalid"
    ),

    /**
     * The requested scope is invalid, unknown, or malformed.
     */
    INVALID_SCOPE(
        BAD_REQUEST,
        "invalid_scope",
        "description.oauth2.invalid"
    ),

    /**
     * The provided authorization grant (e.g., authorization code, resource owner credentials)
     * or refresh token is invalid, expired, revoked, does not match the redirection URI used in
     * the authorization request, or was issued to another client.
     */
    INVALID_GRANT(
        BAD_REQUEST,
        "invalid_grant",
        "description.oauth2.invalid"
    ),

    /**
     * The authorization grant type is not supported by the authorization server.
     */
    UNSUPPORTED_GRANT_TYPE(
        BAD_REQUEST,
        "unsupported_grant_type",
        "description.oauth2.invalid"
    ),

    /**
     * Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method).
     */
    INVALID_CLIENT(
        UNAUTHORIZED,
        "invalid_client",
        "description.oauth2.invalid_client"
    ),

    /**
     * The authorization server encountered an unexpected condition that prevented it from fulfilling the request.
     */
    SERVER_ERROR(
        INTERNAL_SERVER_ERROR,
        "server_error",
        "description.internal_server_error"
    )
}

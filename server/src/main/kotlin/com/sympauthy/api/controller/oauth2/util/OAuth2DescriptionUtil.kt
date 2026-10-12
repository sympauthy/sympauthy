package com.sympauthy.api.controller.oauth2.util

/**
 * What the published specification says about the parameters a client authenticates with, on every
 * endpoint of `/api/oauth2` that reads them. The token endpoint appends what it lets a public client do.
 */
const val CLIENT_ID_PARAMETER_DESCRIPTION = "Identifier of the client, when it sends its credentials in the " +
    "body rather than with HTTP Basic authentication."

/**
 * @see CLIENT_ID_PARAMETER_DESCRIPTION
 */
const val CLIENT_SECRET_PARAMETER_DESCRIPTION = "Secret of the client, when it sends its credentials in the " +
    "body rather than with HTTP Basic authentication."

/**
 * What the published specification says about `token_type_hint`, on the introspection and the revocation
 * endpoints, which both read it to pick the key the token is verified with.
 */
const val TOKEN_TYPE_HINT_PARAMETER_DESCRIPTION = "Type of `token`. A token of the other type is then not " +
    "recognized. When absent, this server reads the type off the token."

/**
 * What the published specification says about the `401` the introspection and the revocation endpoints
 * answer with: both admit only a confidential client, and challenge every refusal of one.
 */
const val INVALID_CLIENT_UNAUTHORIZED_DESCRIPTION = "- `invalid_client`: no client credentials are sent, " +
    "or the ones sent do not authenticate a client. The `WWW-Authenticate` header names the `Basic` scheme."

/**
 * What the published specification says about the `400` the introspection and the revocation endpoints
 * answer with: both authenticate the client, then read `token`, and refuse nothing else.
 */
const val TOKEN_BAD_REQUEST_DESCRIPTION = "- `invalid_request`: `token` is missing."

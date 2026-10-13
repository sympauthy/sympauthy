package com.sympauthy.api.controller.oauth2.util

/**
 * What the published specification says about the parameters a client authenticates with, on every
 * endpoint of `/api/oauth2` that reads them. An endpoint a public client may call appends what it lets one do.
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
 * endpoints, which both accept it and read the type off the token instead.
 */
const val TOKEN_TYPE_HINT_PARAMETER_DESCRIPTION = "Type of `token`. Ignored: this server reads the type off " +
    "the token itself."

/**
 * What the published specification says about the `401` of an endpoint that admits only a confidential
 * client, which challenges every refusal of one.
 */
const val INVALID_CLIENT_UNAUTHORIZED_DESCRIPTION = "- `invalid_client`: no client credentials are sent, " +
    "or the ones sent do not authenticate a client. The `WWW-Authenticate` header names the `Basic` scheme."

/**
 * What the published specification says about the `400` of an endpoint reading `token` once the client is
 * resolved. An endpoint that also answers a refused client `400` appends that.
 */
const val TOKEN_BAD_REQUEST_DESCRIPTION = "- `invalid_request`: `token` is missing."

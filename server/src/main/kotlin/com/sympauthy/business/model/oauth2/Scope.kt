package com.sympauthy.business.model.oauth2

data class Scope(
    /**
     * Identifier of the scope.
     */
    val scope: String,
    /**
     * True if this scope allows the user to access administration APIs of this authorization server.
     * This scope however does not grant any rights on any client.
     */
    val admin: Boolean,
    /**
     * True if this scope will be exposed by the OpenID configuration endpoint.
     */
    val discoverable: Boolean
)

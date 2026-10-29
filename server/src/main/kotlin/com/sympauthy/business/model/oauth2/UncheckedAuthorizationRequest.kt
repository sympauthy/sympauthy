package com.sympauthy.business.model.oauth2

/**
 * The parameters of an authorization request, OAuth 2.1 §4.1.1, as the client sent them. Nothing here is
 * checked: each is null where the request did not carry it, and refused, if it is, by the use case starting
 * the interactive flow session the request opens.
 */
data class UncheckedAuthorizationRequest(
    val clientId: String?,
    val redirectUri: String?,
    /**
     * The scopes asked for, separated by spaces.
     */
    val scopes: String?,
    val state: String?,
    val nonce: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
    val invitationToken: String?,
    val maxAge: String?,
    /**
     * The `claims` parameter, OpenID Connect Core §5.5, as the JSON text the client sent.
     */
    val claims: String?,
    /**
     * The request object the `request` parameter carries, OpenID Connect Core §6.1.
     */
    val requestObject: String?,
    /**
     * The URL of a request object the `request_uri` parameter carries, OpenID Connect Core §6.2.
     */
    val requestObjectUri: String?
)

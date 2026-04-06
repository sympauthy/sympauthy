package com.sympauthy.business.model.client

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope

data class Client(
    val id: String,
    val secret: String?,

    /**
     * Whether this client is a public client (e.g. SPA, mobile app, CLI tool).
     * Public clients cannot hold credentials securely and must use PKCE for authorization code flows.
     */
    val public: Boolean = false,

    /**
     * The [AuthorizationFlow] the user will go through when redirected to the authorization server by this [Client]
     * for an OAuth2 authorization code flow.
     * null if the client does not support OAuth2 authorization code flow.
     */
    val authorizationFlow: AuthorizationFlow?,

    /**
     * List of redirect URIs that are authorized to be used as a redirect_uri for the OAuth2 authorize endpoint.
     * Compared using exact string matching per OAuth 2.1 (section 7.5.3).
     *
     * If the list is null or empty, then all redirect_uri are allowed.
     *
     * > [OAuth 2.0 Security Best Current Practice](https://www.ietf.org/archive/id/draft-ietf-oauth-security-topics-25.html#name-redirect-uri-validation-att)
     */
    val allowedRedirectUris: List<String>? = null,

    /**
     * List of [Scope] that can be issued to a token request by this [Client].
     */
    val allowedScopes: Set<Scope>? = null,

    /**
     * List of [Scope] that are issued by default to a token request by this [Client] if the [Client] did not provide
     * them explicitly to the authorization endpoint.
     */
    val defaultScopes: List<Scope>? = null
)

package com.sympauthy.business.model.flow

import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import java.time.LocalDateTime
import java.util.*

/**
 * The client's OAuth2 request context attached to an [InteractiveFlowSession] whose
 * [InteractiveFlowSession.purpose] is [FlowPurpose.OAUTH2_AUTHORIZE].
 *
 * Holds the parameters the client provided to the authorize endpoint, plus the consent and grant
 * decisions taken during the flow. Keyed by [sessionId] and fetched via
 * [com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager], never carried on the session
 * itself.
 */
data class InteractiveFlowSessionOAuth2(
    /**
     * Identifier of the [InteractiveFlowSession] this OAuth2 request context is attached to.
     */
    val sessionId: UUID,

    /**
     * The identifier of the client that initiated the authentication.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2.2">Client identifier</a>
     */
    val clientId: String,

    /**
     * The URI where we must redirect the user once the authentication flow is finished.
     */
    val redirectUri: String,

    /**
     * Sanitized scopes requested by the client.
     * Un-allowed and invalid scopes have already been filtered out.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-3.3">Scope</a>
     */
    val requestedScopes: List<String>,

    /**
     * The state passed by the client to the authorize endpoint.
     */
    val state: String? = null,

    /**
     * The nonce passed by the client to the authorize endpoint.
     */
    val nonce: String? = null,

    /**
     * The PKCE code challenge provided during authorization (RFC 7636).
     */
    val codeChallenge: String? = null,

    /**
     * The PKCE code challenge method used (RFC 7636).
     */
    val codeChallengeMethod: CodeChallengeMethod? = null,

    /**
     * The identifier of the invitation used to initiate this authorization.
     * Null if the authorization was not initiated via an invitation.
     */
    val invitationId: UUID? = null,

    /**
     * Consentable scopes that the user consented to during the authorization process.
     */
    val consentedScopes: List<String>? = null,

    /**
     * When the consentable scopes were consented.
     */
    val consentedAt: LocalDateTime? = null,

    /**
     * How the consentable scopes were consented (auto or user).
     */
    val consentedBy: ConsentedBy? = null,

    /**
     * Grantable scopes that were granted to the user through granting rules during the authorization process.
     */
    val grantedScopes: List<String>? = null,

    /**
     * When the grantable scopes were granted.
     */
    val grantedAt: LocalDateTime? = null,

    /**
     * How the grantable scopes were granted (auto or rule).
     */
    val grantedBy: GrantedBy? = null,
)

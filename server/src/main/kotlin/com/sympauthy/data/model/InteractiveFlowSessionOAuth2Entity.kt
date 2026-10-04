package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_session_oauth2")
class InteractiveFlowSessionOAuth2Entity(
    @get:Id
    val sessionId: UUID,

    /**
     * Nullable because the record is also created for a failed authorize request — to preserve the state
     * for replay detection — where the client or the redirect URI could not be resolved. Both are present
     * once the request reaches the ongoing / completed path, and the mapper enforces that when it produces
     * the non-null model. [redirectUri] follows the same rule.
     */
    val clientId: String? = null,
    val redirectUri: String? = null,
    val requestedScopes: Array<String> = emptyArray(),
    val state: String? = null,
    val nonce: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,

    /**
     * The claim ids the `claims` request parameter named for the id token, and the ones it named for
     * `/userinfo`. Empty where the client sent no such parameter, and empty where it named nothing this
     * deployment configures: both are a request asking for no claim by name.
     *
     * They are sanitized before they are written, the way [requestedScopes] is, so a row holds claim ids
     * and never a document that has to be parsed again. What a name here is allowed to add is the claim's
     * own `published-in-when-requested` — see `docs/design/claims.md`.
     */
    val requestedIdTokenClaims: Array<String> = emptyArray(),
    val requestedUserinfoClaims: Array<String> = emptyArray(),

    val invitationId: UUID? = null,

    /**
     * When the end-user proved a credential of the account this authorization is for: a password checked,
     * a third-party provider's callback resolved to the account, or the account created at sign-up. Null
     * until one has been proven.
     *
     * It is what every token this authorization produces states as `auth_time`, so it is the moment the
     * credential verified and never the moment the code was exchanged. Passing a second factor follows in
     * the same session and does not move it.
     */
    val authenticationDate: LocalDateTime? = null,

    val consentedScopes: Array<String>? = null,
    val consentedAt: LocalDateTime? = null,
    val consentedBy: String? = null,

    val grantedScopes: Array<String>? = null,
    val grantedAt: LocalDateTime? = null,
    val grantedBy: String? = null,
)

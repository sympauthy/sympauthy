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

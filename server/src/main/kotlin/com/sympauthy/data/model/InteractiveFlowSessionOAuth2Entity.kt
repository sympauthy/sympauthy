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
     * Nullable because the record is also written for a refused authorize request, holding whatever was
     * known, where the client or the redirect URI could not be resolved. Both are present once the request
     * reaches the ongoing / completed path, and the mapper enforces that when it produces the non-null
     * model. [redirectUri] follows the same rule.
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
     * `/userinfo`. Empty where the client sent no such parameter, and empty where it named no claim this
     * deployment enables: both are a request asking for no claim by name. Sanitized before they are
     * written, so a row holds claim ids and never a document — see `docs/design/claims.md`.
     */
    val requestedIdTokenClaims: Array<String> = emptyArray(),
    val requestedUserinfoClaims: Array<String> = emptyArray(),

    val invitationId: UUID? = null,

    /**
     * The `error` token of [com.sympauthy.business.model.oauth2.OAuth2ErrorCode] answering the refusal of the
     * authorize request this record holds, or null where the request was accepted.
     */
    val requestError: String? = null,

    /**
     * When the person proved a credential of the account this authorization is for, null until one has.
     * It is what every token this authorization produces states as `auth_time`; which credentials set it,
     * and why a second factor does not move it, is `docs/design/security.md`.
     */
    val authenticationDate: LocalDateTime? = null,
    /**
     * Identifier of the password proven at [authenticationDate], which the grant this authorization opens
     * records. Null where the credential proven was not a password, and until a reset's new password is
     * written as the flow completes.
     */
    val passwordId: UUID? = null,

    val consentedScopes: Array<String>? = null,
    val consentedAt: LocalDateTime? = null,
    val consentedBy: String? = null,

    val grantedScopes: Array<String>? = null,
    val grantedAt: LocalDateTime? = null,
    val grantedBy: String? = null,
)

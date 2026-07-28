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

    // Authorize endpoint fields
    val clientId: String,
    val redirectUri: String,
    val requestedScopes: Array<String> = emptyArray(),
    val state: String? = null,
    val nonce: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,

    // Invitation
    val invitationId: UUID? = null,

    // Consent
    val consentedScopes: Array<String>? = null,
    val consentedAt: LocalDateTime? = null,
    val consentedBy: String? = null,

    // Granting / Authorization
    val grantedScopes: Array<String>? = null,
    val grantedAt: LocalDateTime? = null,
    val grantedBy: String? = null,
)

package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.DataType
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("authorize_attempts")
class AuthorizeAttemptEntity(
    // Attempt metadata
    val attemptDate: LocalDateTime,
    val authorizationFlowId: String? = null,
    val expirationDate: LocalDateTime,

    // Authorize endpoint fields
    val clientId: String? = null,
    val redirectUri: String? = null,
    val requestedScopes: Array<String> = emptyArray(),
    val state: String? = null,
    val nonce: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,

    // User identification
    val userId: UUID? = null,

    // Consent
    val consentedScopes: Array<String>? = null,
    val consentedAt: LocalDateTime? = null,
    val consentedBy: String? = null,

    // MFA
    val mfaPassedDate: LocalDateTime? = null,

    // Granting / Authorization
    val grantedScopes: Array<String>? = null,
    val grantedAt: LocalDateTime? = null,
    val grantedBy: String? = null,
    val completeDate: LocalDateTime? = null,

    // Error
    val errorDate: LocalDateTime? = null,
    val errorDetailsId: String? = null,
    val errorDescriptionId: String? = null,
    @MappedProperty(type = DataType.JSON)
    val errorValues: Map<String, String>? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

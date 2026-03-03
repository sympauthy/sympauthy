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
    val attemptDate: LocalDateTime,
    val clientId: String? = null,
    val authorizationFlowId: String? = null,
    val redirectUri: String? = null,
    val requestedScopes: Array<String> = emptyArray(),
    val state: String? = null,
    val nonce: String? = null,
    val userId: UUID? = null,
    val grantedScopes: Array<String>? = null,
    val errorDate: LocalDateTime? = null,
    val errorDetailsId: String? = null,
    val errorDescriptionId: String? = null,
    @MappedProperty(type = DataType.JSON)
    val errorValues: Map<String, String>? = null,
    val completeDate: LocalDateTime? = null,
    val mfaPassedDate: LocalDateTime? = null,
    val expirationDate: LocalDateTime
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

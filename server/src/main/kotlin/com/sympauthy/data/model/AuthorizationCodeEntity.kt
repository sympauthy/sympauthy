package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("authorization_codes")
data class AuthorizationCodeEntity(
    @get:Id val sessionId: UUID,
    val code: String,
    val creationDate: LocalDateTime,
    val expirationDate: LocalDateTime
)

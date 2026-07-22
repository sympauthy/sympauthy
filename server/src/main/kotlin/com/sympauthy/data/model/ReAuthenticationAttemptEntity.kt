package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("reauthentication_attempts")
class ReAuthenticationAttemptEntity(
    val targetUserId: UUID,
    val purpose: String,
    val attemptDate: LocalDateTime,
    val expirationDate: LocalDateTime,
    val passedDate: LocalDateTime? = null,
    val passedMethod: String? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

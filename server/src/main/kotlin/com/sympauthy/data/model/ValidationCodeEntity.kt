package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("validation_codes")
class ValidationCodeEntity(
    val code: String,
    val userId: UUID,
    val media: String,
    val reasons: Array<String>,
    val attemptId: UUID?,

    val creationDate: LocalDateTime = LocalDateTime.now(),
    val resendDate: LocalDateTime?,
    val validationDate: LocalDateTime? = null,
    val expirationDate: LocalDateTime
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("totp_enrollments")
class TotpEnrollmentEntity(
    val userId: UUID,
    val secret: ByteArray,
    val creationDate: LocalDateTime = LocalDateTime.now(),
    val confirmedDate: LocalDateTime?
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

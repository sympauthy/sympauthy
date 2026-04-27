package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("consents")
class ConsentEntity(
    val userId: UUID,
    val audienceId: String,
    val promptedByClientId: String,
    val scopes: Array<String>,
    val consentedAt: LocalDateTime,
    val revokedAt: LocalDateTime? = null,
    val revokedBy: String? = null,
    val revokedById: UUID? = null
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

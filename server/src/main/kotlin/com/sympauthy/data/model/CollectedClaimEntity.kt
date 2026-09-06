package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("collected_claims")
class CollectedClaimEntity(
    var userId: UUID,
    var claim: String,
    var value: String?,
    var verified: Boolean?,
    var collectionDate: LocalDateTime,
    var verificationDate: LocalDateTime?,
    override var sessionId: UUID?
) : SessionScoped {
    // This id has no real use, we should use a composed primary key here instead.
    // But since we cannot query on embedded key using Criteria API, we must keep everything flat...
    @Id
    @GeneratedValue
    var id: UUID? = null
}

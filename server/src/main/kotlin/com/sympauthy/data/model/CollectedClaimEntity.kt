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
    /**
     * No use beyond being the single-column id Micronaut Data requires: a composed key over the user, the
     * claim and the session would fit the table better, but the Criteria API cannot query an embedded one,
     * so everything stays flat.
     */
    @Id
    @GeneratedValue
    var id: UUID? = null
}

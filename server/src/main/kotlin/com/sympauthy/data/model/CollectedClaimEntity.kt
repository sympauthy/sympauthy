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
    /**
     * The value as the end-user gave it, never folded, which is what every reader is answered with.
     */
    var value: String?,
    /**
     * A hash of the folded value — [value] cleaned by the claim's type, unquoted and lowercased — and
     * nothing else. **It answers equality under folding and no other question**, and two rows sharing it
     * are candidates rather than a match: a caller acting on a row this selected re-checks the folded
     * value itself. Its mapping is fixed for the life of the schema. See
     * `docs/design/identifier-claims.md`.
     */
    var foldedEqualityHash: Long?,
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

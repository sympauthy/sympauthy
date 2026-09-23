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
     * The value as the end-user gave it, which is what every reader is answered with. Nothing folds it:
     * how somebody capitalises their own name is theirs to decide.
     */
    var value: String?,
    /**
     * The same value in the one spelling every comparison on it is made in — cleaned by the claim's type
     * and lowercased, and without the quoting [value] carries.
     *
     * An identifier is matched on this rather than on [value], so one address reaches its account however
     * it was typed and two accounts cannot hold it in two spellings. Kept as a column rather than computed
     * per query because a `LOWER(...)` comparison is spelled differently by each dialect and is invisible
     * to [com.sympauthy.business.manager.lock.LockKey.IdentifierValue], which hashes a string and never
     * sees a query. See `docs/identifier-claims.md`.
     */
    var comparisonValue: String?,
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

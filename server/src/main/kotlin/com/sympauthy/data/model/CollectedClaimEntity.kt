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
     * A hash of the folded value — [value] cleaned by the claim's type, unquoted and lowercased — and
     * nothing else. **It answers equality under folding and no other question**: it does not order, it
     * does not prefix-match, and two rows sharing it are candidates rather than a match.
     *
     * It is what an identifier is looked up by, so one address reaches its account however it was typed,
     * and it is a fixed-width integer so that the index and the comparison cost the same whatever the
     * value's length. A caller acting on a row this selected re-checks the folded value itself: a hash
     * this wide is cheap to collide on purpose, and a collision here would merge a provider into a
     * stranger's account. See `docs/identifier-claims.md`.
     *
     * The mapping is fixed for the life of the schema, for the reason
     * [com.sympauthy.business.manager.lock.LockKey.stripe] gives: two versions disagreeing about it make
     * every stored row unfindable.
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

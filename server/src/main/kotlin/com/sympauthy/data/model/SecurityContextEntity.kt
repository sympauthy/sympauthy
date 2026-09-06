package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("security_contexts")
class SecurityContextEntity(
    /**
     * The user this place has been seen to be theirs, or null while nobody has been attached to it.
     *
     * Null is what makes the row an unknown one: it came from an abandoned flow, a failed sign-in or
     * probing, and it is kept for a day rather than for months.
     */
    val userId: UUID? = null,
    /**
     * What makes two sightings the same place: the SHA-256 of the normalised address and user agent,
     * and nothing else.
     *
     * It is a deduplication key rather than a device fingerprint. Geo is derived from the address, so
     * folding it in would mint a row every time a provider's city database moved, and it is a column
     * rather than an expression so the unique index is on a fixed-width value.
     */
    val fingerprint: String,
    val ip: String? = null,
    val userAgent: String? = null,
    val country: String? = null,
    /** The region as a code — `TX` rather than `Texas` — whichever proxy published it. */
    val region: String? = null,
    val city: String? = null,
    val firstSeenDate: LocalDateTime,
    val lastSeenDate: LocalDateTime,
    /** How many requests have been seen from this place, counting the one that created the row. */
    val observationCount: Int,
    /**
     * When this row is collected, counted from the last sighting rather than the first.
     *
     * A context is a place someone keeps signing in from, so counting from the first would delete a
     * person's home address after six months of using it. It is indexed, which no other expiration
     * column in this schema is: the sweep runs against a row per place per person for half a year.
     */
    val expirationDate: LocalDateTime,
    /**
     * What the client's access-review webhook last answered about this place, as the name of a
     * [com.sympauthy.business.model.securitycontext.AccessReviewDecision], or null while it has never
     * been asked.
     *
     * It is what the `new-context` trigger reads, and the reason the trigger is a property of the
     * decision rather than of the sighting: keyed on the sighting, the row the first attempt writes
     * would make the second attempt familiar.
     */
    val lastDecision: String? = null,
    val lastDecisionDate: LocalDateTime? = null
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

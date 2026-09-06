package com.sympauthy.business.model.securitycontext

import java.time.LocalDateTime
import java.util.*

/**
 * A place a person has been seen signing in from, kept once however many times they return to it.
 *
 * This is the stored record an [ObservedSecurityContext] becomes: it has an identity, the two dates
 * between which it has been seen, and how many times. Nothing here is scored — the server records
 * the material and a client decides what it means.
 *
 * A context outlives the sessions that observed it, so it names none of them: an interactive flow
 * session carries the contexts it has been seen in, and this row is deleted on its own schedule — a
 * day where nobody was ever attached to it, and months where somebody was.
 */
data class SecurityContext(
    val id: UUID,
    /** The user this place is theirs, or null while the sighting belongs to nobody yet. */
    val userId: UUID?,
    /**
     * What makes two sightings the same place: the SHA-256 of the normalised address and user agent,
     * and nothing else.
     */
    val fingerprint: String,
    val ip: String?,
    val userAgent: String?,
    /**
     * Where the proxy in front of this deployment placed the address, as of the sighting that
     * created the row: a later sighting from the same place does not restate it, so a moved city
     * database does not rewrite a history.
     */
    val geo: SecurityContextGeo,
    val firstSeenDate: LocalDateTime,
    val lastSeenDate: LocalDateTime,
    /** How many requests have been seen from this place, counting the one that created the row. */
    val observationCount: Int,
    /** When this row is collected, counted from [lastSeenDate]. */
    val expirationDate: LocalDateTime,
    /**
     * What the client's access-review webhook last answered about this place, or null while it has
     * never been asked. A webhook that failed to answer records nothing here: one timeout must not
     * stamp an allow and disarm the trigger for good.
     */
    val lastDecision: AccessReviewDecision? = null,
    val lastDecisionDate: LocalDateTime? = null
)

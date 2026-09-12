package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

/**
 * A place one person has been seen signing in from, deduplicated rather than appended per sighting.
 *
 * A row is written only once a flow has completed, by which point its account is committed — which is
 * what keeps [userId]'s foreign key clear of the accounts `ProvisionalAccountManager.deleteAbandoned`
 * collects. `UserRepository.findAbandoned` carries that classification, where the rule lives.
 */
@Serdeable
@MappedEntity("user_security_contexts")
class UserSecurityContextEntity(
    val userId: UUID,
    /**
     * The SHA-256 of [ip] and [userAgent], which is what makes two sightings the same place.
     *
     * Stored rather than derived at read time so the unique index is on a fixed-width value instead of
     * a user-agent string running to hundreds of characters.
     */
    val fingerprint: String,
    /**
     * The address, canonicalised — parsed as a literal and re-rendered — rather than as the edge
     * spelled it.
     *
     * It is the form [fingerprint] was computed from, so the column and the key never disagree, and it
     * is what collapses the two spellings of one address that `SecurityContextUtil` can answer with
     * depending on whether a header or the socket answered.
     */
    val ip: String,
    /**
     * What the caller claimed, bounded to the length the fingerprint was taken over so a row cannot be
     * made arbitrarily wide by whoever is being recorded.
     */
    val userAgent: String?,
    val countryCode: String? = null,
    val regionCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val timeZone: String? = null,
    val firstSeenDate: LocalDateTime,
    /**
     * When this place was last seen, which is what retention is measured from: a place someone keeps
     * signing in from is kept for as long as they keep using it.
     *
     * There is no expiration column beside it. One retention applies to every row and an operator may
     * change it, so a stamped expiry would leave the rows already written on the old policy.
     */
    val lastSeenDate: LocalDateTime,
    val observationCount: Int = 1
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

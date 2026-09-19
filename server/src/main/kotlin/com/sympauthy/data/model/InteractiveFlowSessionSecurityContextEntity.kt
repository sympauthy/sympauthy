package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

/**
 * A place one interactive flow session was driven from, deduplicated rather than appended per request.
 *
 * It is attached to the session rather than to the person because at the moment it is written there
 * may be no person yet — and on most of these rows there never will be — and because a flow that is
 * abandoned should leave nothing behind: the rows are collected with their session by
 * `InteractiveFlowSessionCleaner`, so nothing here needs a retention policy of its own.
 *
 * **It is [UserSecurityContextEntity]'s shape one scope down**, keyed on the same fingerprint and
 * carrying the same first/last/count, because a session driven from one place for eleven requests is
 * one row that says eleven rather than eleven rows.
 *
 * **[provenDate] is the whole of the fold's contract.** Every request that touches a session writes
 * here, which includes every request made by whoever holds the session's state — `docs/security.md`
 * says that carries no identity and travels in a URL — so the fold reads the latest row a credential
 * proof stamped and is blind to the rest by construction rather than by a filter somebody has to
 * remember to write.
 */
@Serdeable
@MappedEntity("interactive_flow_session_security_context")
class InteractiveFlowSessionSecurityContextEntity(
    val sessionId: UUID,
    /** The key the deduplication matches on, taken here so the raw value is canonicalised exactly once. */
    val fingerprint: String,
    val ip: String,
    val userAgent: String? = null,
    val countryCode: String? = null,
    val regionCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val timeZone: String? = null,
    val firstSeenDate: LocalDateTime,
    val lastSeenDate: LocalDateTime,
    val observationCount: Int = 1,
    /**
     * When a credential last verified *and* resolved this session's user at this place, or null for a
     * place nothing but a request was seen from.
     *
     * It says when rather than whether, so the fold picks the last proof of a flow that proved one
     * twice — a sign-in and then a re-authentication — the way replacing a single row used to.
     */
    val provenDate: LocalDateTime? = null
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

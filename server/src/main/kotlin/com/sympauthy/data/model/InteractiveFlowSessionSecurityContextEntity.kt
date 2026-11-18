package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

/**
 * A place one interactive flow session was driven from, deduplicated rather than appended per request:
 * [UserSecurityContextEntity]'s shape one scope down, keyed on the same fingerprint and carrying the
 * same first/last/count. The rows are collected with their session, so nothing here needs a retention
 * policy of its own.
 *
 * **[provenDate] is the whole of the fold's contract.** The fold reads the latest row a credential
 * proof stamped and is blind to the rest by construction rather than by a filter somebody has to
 * remember to write; `docs/design/security-context.md` says why.
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
     * place nothing but a request was seen from. It says when rather than whether, so the fold picks the
     * last proof of a flow that proved one twice — a sign-in and then a re-authentication.
     */
    val provenDate: LocalDateTime? = null
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

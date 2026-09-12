package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

/**
 * Where the person a session belongs to was observed presenting a credential, held until the flow
 * completes and it can be folded into their [UserSecurityContextEntity].
 *
 * It is attached to the session rather than to the person because at the moment it is written there
 * may be no person yet, and because a flow that is abandoned should leave nothing behind: the row is
 * collected with its session by `InteractiveFlowSessionCleaner`, so nothing here needs a retention
 * policy of its own.
 *
 * **One row per session, last write wins.** A flow proving a credential twice — a sign-in and then a
 * re-authentication — records the place the person last proved who they were.
 */
@Serdeable
@MappedEntity("interactive_flow_session_security_context")
data class InteractiveFlowSessionSecurityContextEntity(
    @get:Id
    val sessionId: UUID,
    /** The key the fold deduplicates on, taken here so the raw value is canonicalised exactly once. */
    val fingerprint: String,
    val ip: String,
    val userAgent: String? = null,
    val countryCode: String? = null,
    val regionCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val timeZone: String? = null,
    val observedDate: LocalDateTime
)

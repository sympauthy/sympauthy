package com.sympauthy.business.model.flow

import java.time.LocalDateTime
import java.util.*

/**
 * One place an [InteractiveFlowSession] was driven from, as the row attached to that session holds it.
 *
 * Keyed by [sessionId] like the five other attached records, but written and consumed by
 * [com.sympauthy.business.manager.security.UserSecurityContextManager] rather than by a purpose: a row is
 * opened where the session is created and at every request that resolves it, bumped where that place was
 * already seen, and every one of them is deleted as the completing flow folds the proven one into that
 * person's own record. **A session holds one row per distinct place**, so a stalled sign-in — the session
 * an operator most wants to look at — has one, and a session driven from two places is a trail of two.
 *
 * **[provenDate] is what a credential proof left behind, and it is the only thing the fold reads.** The
 * state a flow travels under carries no identity, so anybody holding it can have a place recorded against
 * that session; a row nothing proved says a request arrived and nothing more.
 *
 * Writing is best-effort, so an absence proves nothing about what happened.
 *
 * The geo fields are the edge's words, unaltered, and every one of them is nullable on its own — see
 * [com.sympauthy.business.model.security.SecurityContextGeo], which the observation is built from.
 *
 * **It carries no fingerprint.** That column is the key a place is deduplicated on, and it answers no
 * question a reader of the session has.
 */
data class InteractiveFlowSessionSecurityContext(
    /**
     * Identifier of the [InteractiveFlowSession] this place is attached to.
     */
    val sessionId: UUID,

    /**
     * The address the requests were observed coming from, under the trust model the deployment configured.
     * A deployment that named no proxy records the socket peer, which is that proxy's own address rather
     * than anybody's — see [com.sympauthy.business.model.security.IpSource].
     */
    val ip: String,

    /**
     * The `User-Agent` header as the caller sent it, bounded in length, or null where none arrived.
     */
    val userAgent: String?,

    val countryCode: String?,
    val regionCode: String?,
    val region: String?,
    val city: String?,
    val timeZone: String?,

    /**
     * When this place was first seen driving the session, and when it was last seen.
     */
    val firstSeenDate: LocalDateTime,
    val lastSeenDate: LocalDateTime,

    /**
     * How many requests of this session came from here. A session driven from one place for eleven
     * requests is one row saying eleven rather than eleven rows.
     */
    val observationCount: Int,

    /**
     * When a credential last verified *and* resolved the session's user at this place, or null for a place
     * nothing but a request was seen from.
     */
    val provenDate: LocalDateTime?
)

package com.sympauthy.business.model.flow

import java.time.LocalDateTime
import java.util.*

/**
 * Where the person behind an [InteractiveFlowSession] was observed proving who they are, as the row attached
 * to that session holds it.
 *
 * Keyed by [sessionId] like the five other attached records, but written and consumed by
 * [com.sympauthy.business.manager.security.UserSecurityContextManager] rather than by a purpose: it is staged
 * when a credential verifies and resolves the session's user, and deleted as the completing flow folds it into
 * that person's own record. **A session therefore holds one only between those two moments**, and a stalled
 * sign-in — the session an operator most wants to look at — has none. Staging is best-effort too, so an
 * absence proves nothing about what happened.
 *
 * The geo fields are the edge's words, unaltered, and every one of them is nullable on its own — see
 * [com.sympauthy.business.model.security.SecurityContextGeo], which the observation is built from.
 *
 * **It carries no fingerprint.** That column is the key the fold deduplicates a person's places on, and it
 * answers no question a reader of the session has.
 */
data class InteractiveFlowSessionSecurityContext(
    /**
     * Identifier of the [InteractiveFlowSession] this observation is attached to.
     */
    val sessionId: UUID,

    /**
     * The address the request was observed coming from, under the trust model the deployment configured. A
     * deployment that named no proxy records the socket peer, which is that proxy's own address rather than
     * anybody's — see [com.sympauthy.business.model.security.IpSource].
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
     * When the observation was taken, which is when the credential was proven rather than when the session
     * started.
     */
    val observedDate: LocalDateTime
)

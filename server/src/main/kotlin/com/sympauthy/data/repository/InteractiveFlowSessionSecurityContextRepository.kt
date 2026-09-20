package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionSecurityContextEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * [observe] is the one statement on the hot path of every flow request, and it is the one method here
 * whose SQL each dialect spells for itself: an upsert has no spelling they share, and a
 * read-then-write in its place would be two round-trips and a race the unique index over
 * `(session_id, fingerprint)` would then refuse. `docs/data-layer-code-standard.md` carries the rule
 * that lets a statement no intersection can express be spelled once per dialect.
 */
interface InteractiveFlowSessionSecurityContextRepository :
    CoroutineCrudRepository<InteractiveFlowSessionSecurityContextEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): List<InteractiveFlowSessionSecurityContextEntity>

    /**
     * Record that [sessionId] was driven from the place [fingerprint] names at [observedDate], and answer
     * how many rows that moved.
     *
     * The place is inserted where the session has fewer than [maxPlaces] of them and bumped where it is
     * already one of them — so a session at the cap goes on counting the places it already holds and
     * **answers zero** for a new one, which is the caller's signal that a cap was reached rather than a
     * failure, and the caller's signal to make room with [deleteLeastRecentPlace]. The count is taken by
     * the same statement that inserts, so two requests racing at the bound may leave one place more than
     * [maxPlaces]: it bounds what a session can mint rather than stating an invariant.
     *
     * The location is overwritten on every sighting, for the reason the fold overwrites it: a place seen
     * again says where that address is now rather than where an edge's database put it the first time.
     * The address and the user agent are not, since the fingerprint is what they are.
     *
     * It never touches `proven_date`: what a credential proof leaves behind is [markProven]'s, and it
     * stamps the row this already wrote for that same request.
     */
    suspend fun observe(
        sessionId: UUID,
        fingerprint: String,
        ip: String,
        userAgent: String?,
        countryCode: String?,
        regionCode: String?,
        region: String?,
        city: String?,
        timeZone: String?,
        observedDate: LocalDateTime,
        maxPlaces: Int
    ): Int

    /**
     * Stamp the place [fingerprint] names as one a credential was proven at, at [provenDate], and answer
     * how many rows that moved.
     *
     * **It only ever stamps a place the same request already recorded**, which is what keeps one request
     * to one row: [observe] must already have written it. A place that is no longer there answers zero
     * rather than opening one — the sighting is lost, which is
     * what the caller logs, and nothing is written that the fold would then read as a proof.
     */
    @Query(
        """
        UPDATE interactive_flow_session_security_context
        SET proven_date = :provenDate
        WHERE session_id = :sessionId AND fingerprint = :fingerprint
        """
    )
    suspend fun markProven(sessionId: UUID, fingerprint: String, provenDate: LocalDateTime): Int

    /**
     * Drop the one place of [sessionId] worth keeping least, and answer how many rows that removed —
     * zero where the session holds none.
     *
     * **Worth keeping least is least recently seen, and a place a credential was proven at goes last.**
     * The first is what a session is read for: where it is being driven from now, which the place nobody
     * has been seen at for longest answers worst. The second is what the fold reads, and the only thing
     * on this table nobody can write without a credential — so every unproven place makes room before it.
     */
    @Query(
        """
        DELETE FROM interactive_flow_session_security_context
        WHERE id = (
            SELECT id FROM interactive_flow_session_security_context
            WHERE session_id = :sessionId
            ORDER BY proven_date NULLS FIRST, last_seen_date, id
            LIMIT 1
        )
        """
    )
    suspend fun deleteLeastRecentPlace(sessionId: UUID): Int

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

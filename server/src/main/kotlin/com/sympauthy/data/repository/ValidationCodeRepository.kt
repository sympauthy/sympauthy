package com.sympauthy.data.repository

import com.sympauthy.data.model.ValidationCodeEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * [insertUnlessCodeHeld] is the one method here whose SQL each dialect spells for itself: an insert that does
 * nothing on a conflict has no spelling they share, and a read-then-insert in its place would be a race the
 * unique index over `(session_id, code)` would then refuse. `docs/standards/data-layer-code-standard.md` carries
 * the rule that lets a statement no intersection can express be spelled once per dialect.
 */
interface ValidationCodeRepository : CoroutineCrudRepository<ValidationCodeEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): List<ValidationCodeEntity>

    suspend fun findBySessionIdAndCode(sessionId: UUID, code: String): ValidationCodeEntity?

    suspend fun findBySessionIdAndMedia(sessionId: UUID, media: String): List<ValidationCodeEntity>

    /**
     * Store [code] for [sessionId] unless the session already holds it, and answer how many rows that inserted.
     *
     * A code the session already holds **answers zero** rather than raising the unique violation, so a caller
     * inside a transaction can draw another: PostgreSQL aborts the transaction a violation is raised in, and
     * refuses every statement after it.
     */
    suspend fun insertUnlessCodeHeld(
        code: String,
        userId: UUID,
        media: String,
        reasons: Array<String>,
        sessionId: UUID,
        creationDate: LocalDateTime,
        resendDate: LocalDateTime?,
        expirationDate: LocalDateTime
    ): Int

    suspend fun deleteByIds(ids: List<UUID>): Int

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

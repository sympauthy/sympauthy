package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthorizationCodeEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface AuthorizationCodeRepository : CoroutineCrudRepository<AuthorizationCodeEntity, UUID> {

    /**
     * Write the exchange date of the code of the session [sessionId], guarded on it having none yet.
     *
     * It answers 1 when the code had not been exchanged and now is, and 0 when it already was or the
     * session holds no code. The row lock the update takes is what decides between two of them racing
     * over one code: the second waits for the first to end, and reads the date it committed.
     */
    @Query(
        """
        UPDATE authorization_codes
        SET exchange_date = :exchangeDate
        WHERE session_id = :sessionId AND exchange_date IS NULL
        """
    )
    suspend fun updateExchangeDate(sessionId: UUID, exchangeDate: LocalDateTime): Int

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

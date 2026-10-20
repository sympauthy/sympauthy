package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthorizationCodeEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface AuthorizationCodeRepository : CoroutineCrudRepository<AuthorizationCodeEntity, UUID> {

    /**
     * Write the exchange date of the code of the grant [grantId], guarded on it having none yet.
     *
     * It answers 1 when the code had not been exchanged and now is, and 0 when it already was or the
     * grant holds no code. The row lock the update takes is what decides between two of them racing
     * over one code: the second waits for the first to end, and reads the date it committed.
     */
    @Query(
        """
        UPDATE authorization_codes
        SET exchange_date = :exchangeDate
        WHERE grant_id = :grantId AND exchange_date IS NULL
        """
    )
    suspend fun updateExchangeDate(grantId: UUID, exchangeDate: LocalDateTime): Int

    suspend fun findByCode(code: String): AuthorizationCodeEntity?

    /**
     * The code issued under the grant the interactive flow session [sessionId] opened, null where the session
     * opened none or the code is gone. A code names no session, so it is reached through its grant.
     */
    @Query(
        """
        SELECT c.* FROM authorization_codes AS c
        JOIN grants AS g ON g.id = c.grant_id
        WHERE g.session_id = :sessionId
        """
    )
    suspend fun findBySessionId(sessionId: UUID): AuthorizationCodeEntity?

    /**
     * Delete every code that expired before [expirationDate], exchanged or not, and answer how many there
     * were.
     */
    suspend fun deleteByExpirationDateLessThan(expirationDate: LocalDateTime): Int
}

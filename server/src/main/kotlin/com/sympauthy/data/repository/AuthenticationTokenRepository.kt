package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * A token is revoked through its grant, so nothing here writes a revocation: the token rows are written once
 * and deleted once.
 */
interface AuthenticationTokenRepository : CoroutineCrudRepository<AuthenticationTokenEntity, UUID> {

    /**
     * Count the tokens issued under the grant identified by [grantId] that have not expired at [now].
     *
     * It is a raw query because the derived form cannot group the expiry, which a token without an
     * expiration date passes.
     */
    @Query(
        """
        SELECT COUNT(*) FROM authentication_tokens
        WHERE grant_id = :grantId AND (expiration_date IS NULL OR expiration_date >= :now)
        """
    )
    suspend fun countLiveByGrantId(grantId: UUID, now: LocalDateTime): Long

    /**
     * Delete every token that expired before [expirationDate], and answer how many there were. A token
     * without an expiration date never has.
     */
    suspend fun deleteByExpirationDateLessThan(expirationDate: LocalDateTime): Int

    /**
     * Delete every token issued under a grant that has been revoked, and answer how many there were.
     */
    @Query(
        """
        DELETE FROM authentication_tokens
        WHERE grant_id IN (SELECT g.id FROM grants g WHERE g.revoked_at IS NOT NULL)
        """
    )
    suspend fun deleteUnderRevokedGrants(): Int
}

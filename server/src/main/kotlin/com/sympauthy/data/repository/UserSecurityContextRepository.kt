package com.sympauthy.data.repository

import com.sympauthy.data.model.UserSecurityContextEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * The sweep claims a bounded batch rather than issuing one unbounded `DELETE`, because `DELETE … LIMIT`
 * is no part of PostgreSQL and one statement over a table holding a row per place per person is a
 * transaction whose size an operator sets by lowering a retention.
 *
 * [claimExpired] therefore follows the claim in `docs/locking-standard.md`,
 * and [deleteByIdInAndLastSeenDateLessThan] re-asserts the predicate the claim selected by: the fold
 * writes this table too, so a row seen again between the claim and the delete has to survive it.
 */
interface UserSecurityContextRepository : CoroutineCrudRepository<UserSecurityContextEntity, UUID> {

    suspend fun findByUserIdAndFingerprint(userId: UUID, fingerprint: String): UserSecurityContextEntity?

    suspend fun updateLastSeenDate(
        @Id id: UUID,
        lastSeenDate: LocalDateTime,
        observationCount: Int,
        countryCode: String?,
        regionCode: String?,
        region: String?,
        city: String?,
        timeZone: String?
    ): Int

    @Query(
        """
        SELECT * FROM user_security_contexts
        WHERE last_seen_date < :cutoff
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """
    )
    suspend fun claimExpired(cutoff: LocalDateTime, limit: Int): List<UserSecurityContextEntity>

    suspend fun deleteByIdInAndLastSeenDateLessThan(ids: List<UUID>, cutoff: LocalDateTime): Int
}

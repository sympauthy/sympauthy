package com.sympauthy.data.repository

import com.sympauthy.data.model.ConsentEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

/**
 * The audience page is spelled twice — once unfiltered, once probing a provider link — rather than once
 * with a nullable provider id, because the unfiltered page has to admit users with no provider link at
 * all and no wildcard inside the `EXISTS` can express that.
 *
 * Its optional subject is a `COALESCE` rather than an `IS NULL` disjunction, so the parameter keeps
 * exactly one usage the driver can infer a type from. `subject` is `NOT NULL`, so an absent one
 * degenerates to a comparison of the column with itself and matches every link to the provider.
 */
interface ConsentRepository : CoroutineCrudRepository<ConsentEntity, UUID> {

    suspend fun findByUserIdAndAudienceIdAndRevokedAtIsNull(userId: UUID, audienceId: String): ConsentEntity?

    suspend fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<ConsentEntity>

    suspend fun findByAudienceIdAndRevokedAtIsNull(audienceId: String): List<ConsentEntity>

    @Query(
        """
        SELECT * FROM consents
        WHERE audience_id = :audienceId AND revoked_at IS NULL
        ORDER BY consented_at, id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun findActiveByAudienceId(audienceId: String, limit: Int, offset: Int): List<ConsentEntity>

    @Query(
        """
        SELECT COUNT(*) FROM consents
        WHERE audience_id = :audienceId AND revoked_at IS NULL
        """
    )
    suspend fun countActiveByAudienceId(audienceId: String): Long

    @Query(
        """
        SELECT * FROM consents c
        WHERE c.audience_id = :audienceId AND c.revoked_at IS NULL
          AND EXISTS (
            SELECT 1 FROM provider_user_info p
            WHERE p.user_id = c.user_id
              AND p.provider_id = :providerId
              AND p.subject = COALESCE(:subject, p.subject)
          )
        ORDER BY c.consented_at, c.id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun findActiveByAudienceIdAndProvider(
        audienceId: String,
        providerId: String,
        subject: String?,
        limit: Int,
        offset: Int
    ): List<ConsentEntity>

    @Query(
        """
        SELECT COUNT(*) FROM consents c
        WHERE c.audience_id = :audienceId AND c.revoked_at IS NULL
          AND EXISTS (
            SELECT 1 FROM provider_user_info p
            WHERE p.user_id = c.user_id
              AND p.provider_id = :providerId
              AND p.subject = COALESCE(:subject, p.subject)
          )
        """
    )
    suspend fun countActiveByAudienceIdAndProvider(
        audienceId: String,
        providerId: String,
        subject: String?
    ): Long

    suspend fun updateRevokedAt(
        @Id id: UUID,
        revokedAt: LocalDateTime,
        revokedBy: String,
        revokedById: UUID
    ): Int
}

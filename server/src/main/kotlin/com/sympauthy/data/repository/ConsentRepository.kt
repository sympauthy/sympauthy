package com.sympauthy.data.repository

import com.sympauthy.data.model.ConsentEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface ConsentRepository : CoroutineCrudRepository<ConsentEntity, UUID> {

    suspend fun findByUserIdAndAudienceIdAndRevokedAtIsNull(userId: UUID, audienceId: String): ConsentEntity?

    suspend fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<ConsentEntity>

    suspend fun findByAudienceIdAndRevokedAtIsNull(audienceId: String): List<ConsentEntity>

    @Query(
        """
        SELECT c.* FROM consents c
        WHERE c.audience_id = :audienceId AND c.revoked_at IS NULL
        ORDER BY c.consented_at, c.id
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun findActiveByAudienceId(audienceId: String, limit: Int, offset: Int): List<ConsentEntity>

    @Query(
        """
        SELECT COUNT(*) FROM consents c
        WHERE c.audience_id = :audienceId AND c.revoked_at IS NULL
        """
    )
    suspend fun countActiveByAudienceId(audienceId: String): Long

    /**
     * A null [subject] matches every link to [providerId], a non-null one only the link carrying it.
     */
    @Query(
        """
        SELECT c.* FROM consents c
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

    /**
     * A null [subject] matches every link to [providerId], a non-null one only the link carrying it.
     */
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

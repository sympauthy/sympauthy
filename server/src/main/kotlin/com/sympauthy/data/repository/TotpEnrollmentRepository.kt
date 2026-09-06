package com.sympauthy.data.repository

import com.sympauthy.data.model.TotpEnrollmentEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface TotpEnrollmentRepository : CoroutineCrudRepository<TotpEnrollmentEntity, UUID> {

    suspend fun findByUserId(userId: UUID): List<TotpEnrollmentEntity>

    suspend fun findByUserIdAndConfirmedDateIsNotNull(userId: UUID): List<TotpEnrollmentEntity>

    suspend fun findByUserIdAndConfirmedDateIsNull(userId: UUID): List<TotpEnrollmentEntity>

    suspend fun findByIdAndSessionIdIsNull(id: UUID): TotpEnrollmentEntity?

    suspend fun updateConfirmedDate(@Id id: UUID, confirmedDate: LocalDateTime)

    /**
     * Promote every enrollment the account [userId] owns and the interactive flow session
     * [sessionId] created, making them permanent, and answer how many there were.
     *
     * Orthogonal to [TotpEnrollmentEntity.confirmedDate]: an enrollment can be provisional and confirmed at
     * once — the second factor is proven, the account it protects is not yet an account.
     */
    @Query("UPDATE totp_enrollments SET session_id = NULL WHERE user_id = :userId AND session_id = :sessionId")
    suspend fun clearSessionId(userId: UUID, sessionId: UUID): Int

    suspend fun deleteByUserIdIn(userId: List<UUID>): Int
}

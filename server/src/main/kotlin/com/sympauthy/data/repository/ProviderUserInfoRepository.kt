package com.sympauthy.data.repository

import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface ProviderUserInfoRepository : CoroutineCrudRepository<ProviderUserInfoEntity, ProviderUserInfoEntityId> {

    suspend fun findByProviderIdAndSubjectAndSessionIdIsNull(
        providerId: String,
        subject: String
    ): ProviderUserInfoEntity?

    suspend fun findByUserId(userId: UUID): List<ProviderUserInfoEntity>

    suspend fun findByUserIdInList(userId: List<UUID>): List<ProviderUserInfoEntity>

    suspend fun findByProviderIdAndUserId(providerId: String, userId: UUID): ProviderUserInfoEntity?

    suspend fun deleteByProviderIdAndUserId(providerId: String, userId: UUID): Int

    /**
     * Promote every provider link the interactive flow session [sessionId] created, making it permanent, and
     * answer how many there were.
     */
    @Query("UPDATE provider_user_info SET session_id = NULL WHERE session_id = :sessionId")
    suspend fun clearSessionId(sessionId: UUID): Int
}

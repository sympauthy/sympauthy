package com.sympauthy.data.repository

import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface ProviderUserInfoRepository : CoroutineCrudRepository<ProviderUserInfoEntity, ProviderUserInfoEntityId> {

    // --- Confirmed-only lookups ---
    // Provisional rows (authorize_attempt_id set) are scoped to an in-progress attach and MUST be excluded from
    // normal lookups, otherwise a later sign-in could resolve a pending identity without the forced re-login.

    suspend fun findByProviderIdAndSubjectAndAuthorizeAttemptIdIsNull(
        providerId: String,
        subject: String
    ): ProviderUserInfoEntity?

    suspend fun findByUserIdAndAuthorizeAttemptIdIsNull(userId: UUID): List<ProviderUserInfoEntity>

    suspend fun findByUserIdInListAndAuthorizeAttemptIdIsNull(userId: List<UUID>): List<ProviderUserInfoEntity>

    suspend fun findByProviderIdAndUserIdAndAuthorizeAttemptIdIsNull(
        providerId: String,
        userId: UUID
    ): ProviderUserInfoEntity?

    // --- Provisional (scoped) rows ---

    suspend fun findByAuthorizeAttemptId(authorizeAttemptId: UUID): ProviderUserInfoEntity?

    suspend fun deleteByAuthorizeAttemptId(authorizeAttemptId: UUID): Int

    suspend fun deleteByAuthorizeAttemptIdIn(authorizeAttemptIds: List<UUID>): Int

    suspend fun deleteByProviderIdAndUserId(providerId: String, userId: UUID): Int
}

package com.sympauthy.data.repository

import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface ProviderUserInfoRepository : CoroutineCrudRepository<ProviderUserInfoEntity, ProviderUserInfoEntityId> {

    suspend fun findByProviderIdAndSubject(providerId: String, subject: String): ProviderUserInfoEntity?

    suspend fun findByUserId(userId: UUID): List<ProviderUserInfoEntity>

    suspend fun findByUserIdInList(userId: List<UUID>): List<ProviderUserInfoEntity>
}

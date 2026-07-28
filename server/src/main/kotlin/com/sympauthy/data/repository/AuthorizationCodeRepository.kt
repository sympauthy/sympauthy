package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthorizationCodeEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface AuthorizationCodeRepository : CoroutineCrudRepository<AuthorizationCodeEntity, UUID> {
    suspend fun deleteByCode(code: String)

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

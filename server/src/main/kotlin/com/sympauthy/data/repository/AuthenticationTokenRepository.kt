package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface AuthenticationTokenRepository : CoroutineCrudRepository<AuthenticationTokenEntity, UUID> {

    suspend fun updateRevokedById(id: UUID, revoked: Boolean)

    suspend fun updateRevokedByAuthorizeAttemptId(authorizeAttemptId: UUID, revoked: Boolean)

    suspend fun updateRevokedByUserIdAndClientId(userId: UUID, clientId: String, revoked: Boolean)
}

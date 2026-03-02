package com.sympauthy.data.repository

import com.sympauthy.data.model.AuthenticationTokenEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface AuthenticationTokenRepository : CoroutineCrudRepository<AuthenticationTokenEntity, UUID> {

    fun updateRevokedById(id: UUID, revoked: Boolean)

    fun updateRevokedByAuthorizeAttemptId(authorizeAttemptId: UUID, revoked: Boolean)
}

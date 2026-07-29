package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionOAuth2Entity
import io.micronaut.data.annotation.Id
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface InteractiveFlowSessionOAuth2Repository :
    CoroutineCrudRepository<InteractiveFlowSessionOAuth2Entity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionOAuth2Entity?

    suspend fun findByState(state: String): InteractiveFlowSessionOAuth2Entity?

    suspend fun updateConsentedScopes(
        @Id sessionId: UUID,
        consentedScopes: List<String>?,
        consentedAt: LocalDateTime,
        consentedBy: String
    )

    suspend fun updateGrantedScopes(
        @Id sessionId: UUID,
        grantedScopes: List<String>?,
        grantedAt: LocalDateTime,
        grantedBy: String
    )

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

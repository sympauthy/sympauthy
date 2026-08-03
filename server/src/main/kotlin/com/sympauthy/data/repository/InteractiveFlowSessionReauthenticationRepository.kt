package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionReauthenticationEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface InteractiveFlowSessionReauthenticationRepository :
    CoroutineCrudRepository<InteractiveFlowSessionReauthenticationEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionReauthenticationEntity?

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionPasswordResetEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface InteractiveFlowSessionPasswordResetRepository :
    CoroutineCrudRepository<InteractiveFlowSessionPasswordResetEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionPasswordResetEntity?

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

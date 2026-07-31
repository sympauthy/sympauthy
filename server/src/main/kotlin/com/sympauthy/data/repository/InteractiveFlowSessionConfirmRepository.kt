package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionConfirmEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface InteractiveFlowSessionConfirmRepository :
    CoroutineCrudRepository<InteractiveFlowSessionConfirmEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionConfirmEntity?

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionProviderEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface InteractiveFlowSessionProviderRepository :
    CoroutineCrudRepository<InteractiveFlowSessionProviderEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionProviderEntity?

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

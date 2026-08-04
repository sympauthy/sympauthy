package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionLinkProviderEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface InteractiveFlowSessionLinkProviderRepository :
    CoroutineCrudRepository<InteractiveFlowSessionLinkProviderEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionLinkProviderEntity?

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

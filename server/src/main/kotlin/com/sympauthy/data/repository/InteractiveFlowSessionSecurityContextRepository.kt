package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionSecurityContextEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface InteractiveFlowSessionSecurityContextRepository :
    CoroutineCrudRepository<InteractiveFlowSessionSecurityContextEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionSecurityContextEntity?

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

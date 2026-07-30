package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionMfaEnrollmentEntity
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.*

interface InteractiveFlowSessionMfaEnrollmentRepository :
    CoroutineCrudRepository<InteractiveFlowSessionMfaEnrollmentEntity, UUID> {

    suspend fun findBySessionId(sessionId: UUID): InteractiveFlowSessionMfaEnrollmentEntity?

    suspend fun deleteBySessionIdIn(sessionIds: List<UUID>): Int
}

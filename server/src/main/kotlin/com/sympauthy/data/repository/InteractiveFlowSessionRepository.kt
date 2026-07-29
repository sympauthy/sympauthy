package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionEntity
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.time.LocalDateTime
import java.util.*

interface InteractiveFlowSessionRepository : CoroutineCrudRepository<InteractiveFlowSessionEntity, UUID> {

    @Query(
        """
        SELECT * FROM interactive_flow_sessions AS s
        JOIN authorization_codes AS ac ON s.id = ac.session_id
        WHERE ac.code = :code
        """
    )
    suspend fun findByCode(code: String): InteractiveFlowSessionEntity?

    @Query(
        """
        SELECT * FROM interactive_flow_sessions
        WHERE expiration_date < CURRENT_TIMESTAMP
        """
    )
    suspend fun findExpired(): List<InteractiveFlowSessionEntity>

    suspend fun updatePurposes(@Id id: UUID, purposes: List<String>)

    suspend fun updateUserId(@Id id: UUID, userId: UUID, signedUp: Boolean)

    suspend fun updateMfaPassedDate(@Id id: UUID, mfaPassedDate: LocalDateTime)

    suspend fun updateError(
        @Id id: UUID,
        errorDate: LocalDateTime?,
        errorDetailsId: String?,
        errorDescriptionId: String?,
        errorValues: Map<String, String>?
    )

    suspend fun updateCompleteDate(@Id id: UUID, completeDate: LocalDateTime?)

    suspend fun deleteByIds(ids: List<UUID>): Int
}

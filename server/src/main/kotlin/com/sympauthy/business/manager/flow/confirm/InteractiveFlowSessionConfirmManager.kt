package com.sympauthy.business.manager.flow.confirm

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.InteractiveFlowSessionConfirmMapper
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionConfirm
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionConfirmEntity
import com.sympauthy.data.repository.InteractiveFlowSessionConfirmRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Manager in charge of the confirm parameter attached to an [InteractiveFlowSession] by an
 * [com.sympauthy.business.model.flow.InteractiveFlowPurpose.CONFIRM] gate. It provides methods to:
 * - store (upsert) what the end-user is asked to approve and who initiated it.
 * - fetch the confirm record of a session.
 * - mark the confirmation as approved (the resolved marker of the CONFIRM purpose).
 */
@Singleton
class InteractiveFlowSessionConfirmManager(
    @Inject private val confirmRepository: InteractiveFlowSessionConfirmRepository,
    @Inject private val confirmMapper: InteractiveFlowSessionConfirmMapper,
) {

    /**
     * Store what the end-user of [session] is asked to approve, upserting the session's confirm record with the
     * [action] and the [clientId] that initiated it (null for an administrator-initiated action). The record
     * starts unconfirmed.
     */
    suspend fun setConfirm(
        session: OnGoingInteractiveFlowSession,
        action: ConfirmActionType,
        clientId: String? = null
    ): InteractiveFlowSessionConfirm {
        val entity = InteractiveFlowSessionConfirmEntity(
            sessionId = session.id,
            action = action.name,
            clientId = clientId,
            confirmedDate = null
        )
        if (confirmRepository.findBySessionId(session.id) != null) {
            confirmRepository.update(entity)
        } else {
            confirmRepository.save(entity)
        }
        return confirmMapper.toInteractiveFlowSessionConfirm(entity)
    }

    /**
     * Return the [InteractiveFlowSessionConfirm] record attached to the [session], or null if the session
     * carries no confirm gate.
     */
    suspend fun fetchConfirmOrNull(session: InteractiveFlowSession): InteractiveFlowSessionConfirm? {
        return confirmRepository.findBySessionId(session.id)
            ?.let(confirmMapper::toInteractiveFlowSessionConfirm)
    }

    /**
     * Mark the confirmation of [session] as approved by the end-user, resolving the CONFIRM purpose.
     *
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if the session carries no
     * confirm record (the CONFIRM purpose was never set up).
     */
    suspend fun markConfirmed(session: OnGoingInteractiveFlowSession): InteractiveFlowSessionConfirm {
        val entity = confirmRepository.findBySessionId(session.id)
            ?: throw internalBusinessExceptionOf("flow.confirm.missing_record")
        val confirmed = entity.copy(confirmedDate = LocalDateTime.now())
        confirmRepository.update(confirmed)
        return confirmMapper.toInteractiveFlowSessionConfirm(confirmed)
    }
}

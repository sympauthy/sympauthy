package com.sympauthy.business.flow.reset.manager

import com.sympauthy.business.flow.mapper.InteractiveFlowSessionPasswordResetMapper
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.flow.model.InteractiveFlowSessionPasswordReset
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.password.model.HashedPassword
import com.sympauthy.data.model.InteractiveFlowSessionPasswordResetEntity
import com.sympauthy.data.repository.InteractiveFlowSessionPasswordResetRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime
import java.util.*

/**
 * Manager in charge of the password reset record attached to an [InteractiveFlowSession] by a
 * [com.sympauthy.business.flow.model.InteractiveFlowPurpose.PASSWORD_RESET] gate.
 *
 * Kept to the record alone, so the purpose handler reads it without depending on what sends the codes.
 */
@Singleton
class InteractiveFlowSessionPasswordResetManager(
    @Inject private val passwordResetRepository: InteractiveFlowSessionPasswordResetRepository,
    @Inject private val passwordResetMapper: InteractiveFlowSessionPasswordResetMapper,
) {

    /**
     * Return the reset record attached to the [session], or null where the end-user never asked for a reset in
     * it.
     */
    suspend fun fetchPasswordResetOrNull(session: InteractiveFlowSession): InteractiveFlowSessionPasswordReset? {
        return passwordResetRepository.findBySessionId(session.id)
            ?.let(passwordResetMapper::toInteractiveFlowSessionPasswordReset)
    }

    /**
     * Record that the end-user asked, in [session], to reset the password of whichever account holds [login],
     * a code having gone to the account identified by [userId] where it is not null, and return the record.
     *
     * [requestedDate] is when the last code or notice was asked for, which the resend delay runs from: now, unless
     * nothing was sent for this request.
     *
     * A reset asked for again replaces the one before it whole, a code it had proven included: the end-user
     * typed another identifier, and what they proved of the first proves nothing of it.
     */
    suspend fun savePasswordReset(
        session: OnGoingInteractiveFlowSession,
        login: String,
        userId: UUID?,
        requestedDate: LocalDateTime = LocalDateTime.now()
    ): InteractiveFlowSessionPasswordReset {
        val entity = InteractiveFlowSessionPasswordResetEntity(
            sessionId = session.id,
            login = login,
            userId = userId,
            requestedDate = requestedDate
        )
        save(session, entity)
        return passwordResetMapper.toInteractiveFlowSessionPasswordReset(entity)
    }

    /**
     * Record that a code proved the account [passwordReset] names, and return the record.
     */
    suspend fun markCodeProven(
        session: OnGoingInteractiveFlowSession,
        passwordReset: InteractiveFlowSessionPasswordReset
    ): InteractiveFlowSessionPasswordReset = update(session, passwordReset.copy(codeProvenDate = LocalDateTime.now()))

    /**
     * Record the [newPassword] the end-user chose for the reset [passwordReset] holds, and return the record. A
     * password chosen again replaces the one before it.
     */
    suspend fun markPasswordChosen(
        session: OnGoingInteractiveFlowSession,
        passwordReset: InteractiveFlowSessionPasswordReset,
        newPassword: HashedPassword
    ): InteractiveFlowSessionPasswordReset = update(
        session,
        passwordReset.copy(newPassword = newPassword, passwordChosenDate = LocalDateTime.now())
    )

    private suspend fun update(
        session: OnGoingInteractiveFlowSession,
        passwordReset: InteractiveFlowSessionPasswordReset
    ): InteractiveFlowSessionPasswordReset {
        val entity = InteractiveFlowSessionPasswordResetEntity(
            sessionId = session.id,
            login = passwordReset.login,
            userId = passwordReset.userId,
            requestedDate = passwordReset.requestedDate,
            codeProvenDate = passwordReset.codeProvenDate,
            salt = passwordReset.newPassword?.salt ?: ByteArray(0),
            hashedPassword = passwordReset.newPassword?.hash ?: ByteArray(0),
            passwordChosenDate = passwordReset.passwordChosenDate
        )
        passwordResetRepository.update(entity)
        return passwordReset
    }

    private suspend fun save(
        session: OnGoingInteractiveFlowSession,
        entity: InteractiveFlowSessionPasswordResetEntity
    ) {
        if (passwordResetRepository.findBySessionId(session.id) != null) {
            passwordResetRepository.update(entity)
        } else {
            passwordResetRepository.save(entity)
        }
    }
}

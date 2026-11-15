package com.sympauthy.business.mapper

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.flow.InteractiveFlowSessionPasswordReset
import com.sympauthy.business.model.password.HashedPassword
import com.sympauthy.data.model.InteractiveFlowSessionPasswordResetEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [InteractiveFlowSessionPasswordResetEntity] to the
 * [InteractiveFlowSessionPasswordReset] business model.
 *
 * The new password is read only once its choice is dated: the hash and the salt are empty until then, as
 * the entity says. A row whose date and hash disagree is refused rather than read as one or the other.
 */
@Singleton
class InteractiveFlowSessionPasswordResetMapper {

    fun toInteractiveFlowSessionPasswordReset(
        entity: InteractiveFlowSessionPasswordResetEntity
    ): InteractiveFlowSessionPasswordReset {
        return InteractiveFlowSessionPasswordReset(
            sessionId = entity.sessionId,
            login = entity.login,
            userId = entity.userId,
            requestedDate = entity.requestedDate,
            codeProvenDate = entity.codeProvenDate,
            newPassword = newPassword(entity),
            passwordChosenDate = entity.passwordChosenDate
        )
    }

    /**
     * The new password [entity] holds, or null where none is chosen yet, refusing the row where the hash is
     * empty once a password is chosen or present while none is.
     */
    private fun newPassword(entity: InteractiveFlowSessionPasswordResetEntity): HashedPassword? {
        val chosen = entity.passwordChosenDate != null
        if (chosen == entity.hashedPassword.isEmpty()) {
            throw internalBusinessExceptionOf(
                detailsId = "mapper.interactive_flow_session_password_reset.invalid_property",
                values = arrayOf("property" to "hashedPassword")
            )
        }
        if (chosen == entity.salt.isEmpty()) {
            throw internalBusinessExceptionOf(
                detailsId = "mapper.interactive_flow_session_password_reset.invalid_property",
                values = arrayOf("property" to "salt")
            )
        }
        return if (chosen) HashedPassword(salt = entity.salt, hash = entity.hashedPassword) else null
    }
}

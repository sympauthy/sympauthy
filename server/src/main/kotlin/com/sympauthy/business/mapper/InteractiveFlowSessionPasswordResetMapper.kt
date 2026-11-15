package com.sympauthy.business.mapper

import com.sympauthy.business.model.flow.InteractiveFlowSessionPasswordReset
import com.sympauthy.business.model.password.HashedPassword
import com.sympauthy.data.model.InteractiveFlowSessionPasswordResetEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [InteractiveFlowSessionPasswordResetEntity] to the
 * [InteractiveFlowSessionPasswordReset] business model.
 *
 * The new password is read only once its choice is dated: the hash and the salt are empty until then, as
 * the entity says.
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
            newPassword = entity.passwordChosenDate?.let {
                HashedPassword(salt = entity.salt, hash = entity.hashedPassword)
            },
            passwordChosenDate = entity.passwordChosenDate
        )
    }
}

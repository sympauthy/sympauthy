package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.UserEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [UserEntity] to the [User] business model.
 *
 * If the row holds no identifier, or a status [UserStatus] does not name, an internal [BusinessException]
 * "mapper.user.invalid_property" is thrown: a row this server wrote and cannot read back is its own
 * failure rather than the caller's.
 */
@Singleton
class UserMapper {

    fun toUser(entity: UserEntity): User {
        return User(
            id = entity.id ?: throw invalidProperty("id"),
            status = toUserStatus(entity.status),
            creationDate = entity.creationDate,
            sessionId = entity.sessionId,
        )
    }

    /**
     * The status [status] names, refusing the row where it names none.
     */
    fun toUserStatus(status: String): UserStatus {
        return try {
            UserStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            throw invalidProperty("status", e)
        }
    }

    private fun invalidProperty(
        invalidProperty: String,
        cause: Throwable? = null
    ): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.user.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }
}

package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.data.model.UserEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [UserEntity] to the [User] business model.
 *
 * If the row holds a status [UserStatus] does not name, an internal [BusinessException]
 * "mapper.user.invalid_property" is thrown: a row this server wrote and cannot read back is its own
 * failure rather than the caller's.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class UserMapper {

    abstract fun toUser(entity: UserEntity): User

    /**
     * The status [status] names, refusing the row where it names none.
     */
    fun toUserStatus(status: String): UserStatus {
        return try {
            UserStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            throw internalBusinessExceptionOf(
                detailsId = "mapper.user.invalid_property",
                throwable = e,
                values = arrayOf("property" to "status")
            )
        }
    }
}

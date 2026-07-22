package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.reauth.PassedReAuthenticationAttempt
import com.sympauthy.business.model.reauth.PendingReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationAttempt
import com.sympauthy.business.model.reauth.ReAuthenticationMethod
import com.sympauthy.business.model.reauth.ReAuthenticationPurpose
import com.sympauthy.data.model.ReAuthenticationAttemptEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [ReAuthenticationAttemptEntity] to the subclasses of the sealed
 * [ReAuthenticationAttempt]:
 * - [PassedReAuthenticationAttempt] if the [ReAuthenticationAttemptEntity.passedDate] is not null.
 * - [PendingReAuthenticationAttempt] otherwise.
 *
 * If the content of the entity is not valid according to the status of the attempt, an unrecoverable
 * [BusinessException] "mapper.reauthentication_attempt.invalid_property" will be thrown.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class ReAuthenticationAttemptMapper {

    fun toReAuthenticationAttempt(entity: ReAuthenticationAttemptEntity): ReAuthenticationAttempt {
        return if (entity.passedDate != null) {
            toPassedReAuthenticationAttempt(entity)
        } else {
            toPendingReAuthenticationAttempt(entity)
        }
    }

    fun toPendingReAuthenticationAttempt(entity: ReAuthenticationAttemptEntity): PendingReAuthenticationAttempt {
        return PendingReAuthenticationAttempt(
            id = entity.id ?: throw invalidBusinessException("id"),
            targetUserId = entity.targetUserId,
            purpose = mapPurpose(entity.purpose),
            expirationDate = entity.expirationDate,
            attemptDate = entity.attemptDate
        )
    }

    fun toPassedReAuthenticationAttempt(entity: ReAuthenticationAttemptEntity): PassedReAuthenticationAttempt {
        return PassedReAuthenticationAttempt(
            id = entity.id ?: throw invalidBusinessException("id"),
            targetUserId = entity.targetUserId,
            purpose = mapPurpose(entity.purpose),
            expirationDate = entity.expirationDate,
            attemptDate = entity.attemptDate,
            passedDate = entity.passedDate ?: throw invalidBusinessException("passedDate"),
            passedMethod = mapMethod(entity.passedMethod ?: throw invalidBusinessException("passedMethod"))
        )
    }

    private fun mapPurpose(value: String): ReAuthenticationPurpose =
        ReAuthenticationPurpose.entries.firstOrNull { it.name == value }
            ?: throw invalidBusinessException("purpose")

    private fun mapMethod(value: String): ReAuthenticationMethod =
        ReAuthenticationMethod.entries.firstOrNull { it.name == value }
            ?: throw invalidBusinessException("passedMethod")

    private fun invalidBusinessException(invalidProperty: String): BusinessException {
        return businessExceptionOf(
            detailsId = "mapper.reauthentication_attempt.invalid_property",
            values = arrayOf("property" to invalidProperty)
        )
    }
}

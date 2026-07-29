package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.*
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import org.mapstruct.Mapper
import java.time.LocalDateTime

/**
 * Handle the mapping from the [InteractiveFlowSessionEntity] to the subclasses of the sealed
 * [InteractiveFlowSession]. The status of the session is checked to determine the appropriate subclass
 * to map to:
 * - [FailedInteractiveFlowSession] if the [InteractiveFlowSessionEntity.errorDate] is not null, or if the
 *   session has expired.
 * - [CompletedInteractiveFlowSession] if the [InteractiveFlowSessionEntity.completeDate] is not null.
 * - [OnGoingInteractiveFlowSession] otherwise.
 *
 * If the content of the [InteractiveFlowSessionEntity] is not valid, according to the status of the
 * session, an unrecoverable [BusinessException] "mapper.interactive_flow_session.invalid_property" will
 * be thrown.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class InteractiveFlowSessionMapper {

    fun toOnGoingInteractiveFlowSession(entity: InteractiveFlowSessionEntity): OnGoingInteractiveFlowSession {
        return OnGoingInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = entity.purposes.map(InteractiveFlowPurpose::valueOf),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            sessionDate = entity.sessionDate,
            userId = entity.userId,
            mfaPassedDate = entity.mfaPassedDate,
        )
    }

    fun toCompletedInteractiveFlowSession(entity: InteractiveFlowSessionEntity): CompletedInteractiveFlowSession {
        return CompletedInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = entity.purposes.map(InteractiveFlowPurpose::valueOf),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            sessionDate = entity.sessionDate,
            userId = entity.userId ?: throw invalidBusinessException("userId"),
            mfaPassedDate = entity.mfaPassedDate,
            completeDate = entity.completeDate ?: throw invalidBusinessException("completeDate"),
        )
    }

    fun toFailedInteractiveFlowSession(entity: InteractiveFlowSessionEntity): FailedInteractiveFlowSession {
        return FailedInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = entity.purposes.map(InteractiveFlowPurpose::valueOf),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            errorDetailsId = entity.errorDetailsId ?: throw invalidBusinessException("errorDetailsId"),
            errorDescriptionId = entity.errorDescriptionId,
            errorValues = entity.errorValues,
            errorDate = entity.errorDate ?: throw invalidBusinessException("errorDate")
        )
    }

    fun toExpiredInteractiveFlowSession(entity: InteractiveFlowSessionEntity): FailedInteractiveFlowSession {
        return FailedInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = entity.purposes.map(InteractiveFlowPurpose::valueOf),
            flowId = entity.flowId,
            errorDetailsId = "auth.interactive_flow_session.validate.expired",
            errorDescriptionId = "description.oauth2.expired",
            errorValues = emptyMap(),
            expirationDate = entity.expirationDate,
            errorDate = entity.expirationDate,
        )
    }

    fun toInteractiveFlowSession(entity: InteractiveFlowSessionEntity): InteractiveFlowSession {
        return when {
            entity.errorDate != null -> toFailedInteractiveFlowSession(entity)
            entity.expirationDate.isBefore(LocalDateTime.now()) -> toExpiredInteractiveFlowSession(entity)
            entity.completeDate != null -> toCompletedInteractiveFlowSession(entity)
            else -> toOnGoingInteractiveFlowSession(entity)
        }
    }

    private fun invalidBusinessException(invalidProperty: String): BusinessException {
        return businessExceptionOf(
            detailsId = "mapper.interactive_flow_session.invalid_property",
            values = arrayOf("property" to invalidProperty)
        )
    }
}

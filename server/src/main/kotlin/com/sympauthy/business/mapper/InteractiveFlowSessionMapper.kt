package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.*
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import org.mapstruct.Mapper
import java.net.URI
import java.time.LocalDateTime

/**
 * Handle the mapping from the [InteractiveFlowSessionEntity] to the subclasses of the sealed
 * [InteractiveFlowSession]. The status of the session is checked to determine the appropriate subclass
 * to map to:
 * - [FailedInteractiveFlowSession] if the [InteractiveFlowSessionEntity.errorDate] is not null, or if the
 *   session has expired.
 * - [CancelledInteractiveFlowSession] if the [InteractiveFlowSessionEntity.cancelDate] is not null.
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
            initiatingPurpose = InteractiveFlowPurpose.valueOf(entity.initiatingPurpose),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            sessionDate = entity.sessionDate,
            userId = entity.userId,
            signedUp = entity.signedUp,
            completedPurposes = entity.completedPurposes.map(InteractiveFlowPurpose::valueOf),
            mfaPassedDate = entity.mfaPassedDate,
            successRedirectUri = entity.successRedirectUri?.let(URI::create),
            redirectType = entity.redirectType?.let(InteractiveFlowRedirectType::valueOf),
            cancelRedirectUri = entity.cancelRedirectUri?.let(URI::create),
        )
    }

    fun toCompletedInteractiveFlowSession(entity: InteractiveFlowSessionEntity): CompletedInteractiveFlowSession {
        return CompletedInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = entity.purposes.map(InteractiveFlowPurpose::valueOf),
            initiatingPurpose = InteractiveFlowPurpose.valueOf(entity.initiatingPurpose),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            sessionDate = entity.sessionDate,
            userId = entity.userId ?: throw invalidBusinessException("userId"),
            signedUp = entity.signedUp,
            completedPurposes = entity.completedPurposes.map(InteractiveFlowPurpose::valueOf),
            mfaPassedDate = entity.mfaPassedDate,
            completeDate = entity.completeDate ?: throw invalidBusinessException("completeDate"),
            successRedirectUri = entity.successRedirectUri?.let(URI::create)
                ?: throw invalidBusinessException("successRedirectUri"),
            redirectType = entity.redirectType?.let(InteractiveFlowRedirectType::valueOf)
                ?: throw invalidBusinessException("redirectType"),
            cancelRedirectUri = entity.cancelRedirectUri?.let(URI::create),
        )
    }

    fun toCancelledInteractiveFlowSession(entity: InteractiveFlowSessionEntity): CancelledInteractiveFlowSession {
        return CancelledInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = entity.purposes.map(InteractiveFlowPurpose::valueOf),
            initiatingPurpose = InteractiveFlowPurpose.valueOf(entity.initiatingPurpose),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            userId = entity.userId,
            redirectType = entity.redirectType?.let(InteractiveFlowRedirectType::valueOf)
                ?: throw invalidBusinessException("redirectType"),
            successRedirectUri = entity.successRedirectUri?.let(URI::create),
            cancelRedirectUri = entity.cancelRedirectUri?.let(URI::create),
            cancelDate = entity.cancelDate ?: throw invalidBusinessException("cancelDate"),
        )
    }

    fun toFailedInteractiveFlowSession(entity: InteractiveFlowSessionEntity): FailedInteractiveFlowSession {
        return FailedInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = entity.purposes.map(InteractiveFlowPurpose::valueOf),
            initiatingPurpose = InteractiveFlowPurpose.valueOf(entity.initiatingPurpose),
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
            initiatingPurpose = InteractiveFlowPurpose.valueOf(entity.initiatingPurpose),
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
            entity.cancelDate != null -> toCancelledInteractiveFlowSession(entity)
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

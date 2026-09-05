package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
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
 * session, an internal [BusinessException] "mapper.interactive_flow_session.invalid_property" is thrown:
 * a row this server wrote and cannot read back is its own failure rather than the caller's.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class InteractiveFlowSessionMapper {

    fun toOnGoingInteractiveFlowSession(entity: InteractiveFlowSessionEntity): OnGoingInteractiveFlowSession {
        return OnGoingInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = purposes(entity.purposes, "purposes"),
            initiatingPurpose = purpose(entity.initiatingPurpose, "initiatingPurpose"),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            sessionDate = entity.sessionDate,
            version = entity.version,
            userId = entity.userId,
            signedUp = entity.signedUp,
            completedPurposes = purposes(entity.completedPurposes, "completedPurposes"),
            mfaPassedDate = entity.mfaPassedDate,
            successRedirectUri = uri(entity.successRedirectUri, "successRedirectUri"),
            redirectType = redirectType(entity.redirectType),
            cancelRedirectUri = uri(entity.cancelRedirectUri, "cancelRedirectUri"),
        )
    }

    fun toCompletedInteractiveFlowSession(entity: InteractiveFlowSessionEntity): CompletedInteractiveFlowSession {
        return CompletedInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = purposes(entity.purposes, "purposes"),
            initiatingPurpose = purpose(entity.initiatingPurpose, "initiatingPurpose"),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            sessionDate = entity.sessionDate,
            userId = entity.userId ?: throw invalidBusinessException("userId"),
            signedUp = entity.signedUp,
            completedPurposes = purposes(entity.completedPurposes, "completedPurposes"),
            mfaPassedDate = entity.mfaPassedDate,
            completeDate = entity.completeDate ?: throw invalidBusinessException("completeDate"),
            successRedirectUri = uri(entity.successRedirectUri, "successRedirectUri")
                ?: throw invalidBusinessException("successRedirectUri"),
            redirectType = redirectType(entity.redirectType)
                ?: throw invalidBusinessException("redirectType"),
            cancelRedirectUri = uri(entity.cancelRedirectUri, "cancelRedirectUri"),
        )
    }

    fun toCancelledInteractiveFlowSession(entity: InteractiveFlowSessionEntity): CancelledInteractiveFlowSession {
        return CancelledInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = purposes(entity.purposes, "purposes"),
            initiatingPurpose = purpose(entity.initiatingPurpose, "initiatingPurpose"),
            flowId = entity.flowId,
            expirationDate = entity.expirationDate,
            userId = entity.userId,
            redirectType = redirectType(entity.redirectType)
                ?: throw invalidBusinessException("redirectType"),
            successRedirectUri = uri(entity.successRedirectUri, "successRedirectUri"),
            cancelRedirectUri = uri(entity.cancelRedirectUri, "cancelRedirectUri"),
            cancelDate = entity.cancelDate ?: throw invalidBusinessException("cancelDate"),
        )
    }

    fun toFailedInteractiveFlowSession(entity: InteractiveFlowSessionEntity): FailedInteractiveFlowSession {
        return FailedInteractiveFlowSession(
            id = entity.id ?: throw invalidBusinessException("id"),
            purposes = purposes(entity.purposes, "purposes"),
            initiatingPurpose = purpose(entity.initiatingPurpose, "initiatingPurpose"),
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
            purposes = purposes(entity.purposes, "purposes"),
            initiatingPurpose = purpose(entity.initiatingPurpose, "initiatingPurpose"),
            flowId = entity.flowId,
            errorDetailsId = "auth.interactive_flow_session.validate.expired",
            errorDescriptionId = "description.oauth2.expired",
            errorValues = mapOf("expirationDate" to entity.expirationDate.toString()),
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

    /**
     * The purposes the column [property] holds, one per element of [purposes].
     */
    private fun purposes(purposes: Array<String>, property: String) = purposes.map { purpose(it, property) }

    /**
     * The purpose [purpose] names, refusing the column [property] where it names none.
     */
    private fun purpose(purpose: String, property: String): InteractiveFlowPurpose {
        return try {
            InteractiveFlowPurpose.valueOf(purpose)
        } catch (e: IllegalArgumentException) {
            throw invalidBusinessException(property, e)
        }
    }

    /**
     * The redirect type [redirectType] names, or null where the session has no terminal redirect.
     */
    private fun redirectType(redirectType: String?): InteractiveFlowRedirectType? {
        return redirectType?.let {
            try {
                InteractiveFlowRedirectType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                throw invalidBusinessException("redirectType", e)
            }
        }
    }

    /**
     * The [uri] the column [property] holds, or null where it holds none.
     */
    private fun uri(uri: String?, property: String): URI? {
        return uri?.let {
            try {
                URI.create(it)
            } catch (e: IllegalArgumentException) {
                throw invalidBusinessException(property, e)
            }
        }
    }

    private fun invalidBusinessException(
        invalidProperty: String,
        cause: Throwable? = null
    ): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.interactive_flow_session.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }
}

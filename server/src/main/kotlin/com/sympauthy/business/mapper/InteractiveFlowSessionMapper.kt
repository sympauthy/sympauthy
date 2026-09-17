package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.*
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import org.mapstruct.Mapper
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID

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
            initiatingClientId = entity.initiatingClientId,
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
            initiatingClientId = entity.initiatingClientId,
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
            initiatingClientId = entity.initiatingClientId,
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
            initiatingClientId = entity.initiatingClientId,
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
            initiatingClientId = entity.initiatingClientId,
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
     * What became of the session [entity], read from its terminal columns with **expiry evaluated last**.
     *
     * This is not the order [toInteractiveFlowSession] walks, and [InteractiveFlowSessionStatus] carries why
     * the two differ. Read this where the question is what happened to the session; read the projection where
     * the question is what to do with it now.
     */
    fun toStatus(entity: InteractiveFlowSessionEntity): InteractiveFlowSessionStatus = when {
        entity.errorDate != null -> InteractiveFlowSessionStatus.FAILED
        entity.cancelDate != null -> InteractiveFlowSessionStatus.CANCELLED
        entity.completeDate != null -> InteractiveFlowSessionStatus.COMPLETED
        entity.expirationDate.isBefore(LocalDateTime.now()) -> InteractiveFlowSessionStatus.EXPIRED
        else -> InteractiveFlowSessionStatus.ONGOING
    }

    /**
     * The session [entity] as the subclass [status] names, rather than as [toInteractiveFlowSession] would
     * project it.
     *
     * [InteractiveFlowSessionStatus.EXPIRED] maps to an [OnGoingInteractiveFlowSession]: an abandoned session
     * still carries its user, its completed purposes and the date it started, and a reader asking which
     * purpose it stalled on is asking about exactly that session. Handing it over as a
     * [FailedInteractiveFlowSession] instead would drop all three.
     */
    fun toInteractiveFlowSessionAs(
        entity: InteractiveFlowSessionEntity,
        status: InteractiveFlowSessionStatus
    ): InteractiveFlowSession = when (status) {
        InteractiveFlowSessionStatus.ONGOING,
        InteractiveFlowSessionStatus.EXPIRED -> toOnGoingInteractiveFlowSession(entity)

        InteractiveFlowSessionStatus.COMPLETED -> toCompletedInteractiveFlowSession(entity)
        InteractiveFlowSessionStatus.CANCELLED -> toCancelledInteractiveFlowSession(entity)
        InteractiveFlowSessionStatus.FAILED -> toFailedInteractiveFlowSession(entity)
    }

    /**
     * The identifier of the session [entity], refusing a row that has none.
     */
    fun toId(entity: InteractiveFlowSessionEntity): UUID = entity.id ?: throw invalidBusinessException("id")

    /**
     * The purpose the session [entity] says started it, refusing a column naming none.
     */
    fun toInitiatingPurpose(entity: InteractiveFlowSessionEntity): InteractiveFlowPurpose =
        purpose(entity.initiatingPurpose, "initiatingPurpose")

    /**
     * The purposes the session [entity] has resolved, read from its own column.
     *
     * For a reader that has to answer for any status: [CancelledInteractiveFlowSession] and
     * [FailedInteractiveFlowSession] carry no completed-purpose list, and a session in either state is one
     * somebody is asking how far it got.
     */
    fun toCompletedPurposes(entity: InteractiveFlowSessionEntity): List<InteractiveFlowPurpose> =
        purposes(entity.completedPurposes, "completedPurposes")

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

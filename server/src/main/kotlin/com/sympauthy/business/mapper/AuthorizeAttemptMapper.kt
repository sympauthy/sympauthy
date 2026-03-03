package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.data.model.AuthorizeAttemptEntity
import org.mapstruct.Mapper
import java.time.LocalDateTime

/**
 * Handle the mapping from the [AuthorizeAttemptEntity] to the subclasses of the sealed [AuthorizeAttempt].
 * The status of the authorized attempt is checked to determine the appropriate subclass to map to:
 * - [CompletedAuthorizeAttempt] if the [AuthorizeAttemptEntity.completeDate] is not null.
 * - [FailedAuthorizeAttempt] if the [AuthorizeAttemptEntity.errorDate] is not null.
 * - [OnGoingAuthorizeAttempt] otherwise.
 *
 * If the content of the [AuthorizeAttemptEntity] is not valid, according to the status of the attempt,
 * an unrecoverable [BusinessException] "mapper.authorize_attempt.invalid_property" will be thrown.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class AuthorizeAttemptMapper {

    fun toOnGoingAuthorizeAttempt(entity: AuthorizeAttemptEntity): OnGoingAuthorizeAttempt {
        return OnGoingAuthorizeAttempt(
            id = entity.id ?: throw invalidBusinessException("id"),
            authorizationFlowId = entity.authorizationFlowId,
            expirationDate = entity.expirationDate,
            clientId = entity.clientId ?: throw invalidBusinessException("clientId"),
            requestedScopes = entity.requestedScopes.toList(),
            redirectUri = entity.redirectUri ?: throw invalidBusinessException("redirectUri"),
            state = entity.state,
            nonce = entity.nonce,
            userId = entity.userId,
            grantedScopes = entity.grantedScopes?.toList(),
            mfaPassedDate = entity.mfaPassedDate,
            attemptDate = entity.attemptDate
        )
    }

    fun toCompletedAuthorizeAttempt(entity: AuthorizeAttemptEntity): CompletedAuthorizeAttempt {
        return CompletedAuthorizeAttempt(
            id = entity.id ?: throw invalidBusinessException("id"),
            authorizationFlowId = entity.authorizationFlowId,
            expirationDate = entity.expirationDate,
            clientId = entity.clientId ?: throw invalidBusinessException("clientId"),
            requestedScopes = entity.requestedScopes.toList(),
            redirectUri = entity.redirectUri ?: throw invalidBusinessException("redirectUri"),
            state = entity.state,
            nonce = entity.nonce,
            userId = entity.userId ?: throw invalidBusinessException("userId"),
            grantedScopes = entity.grantedScopes?.toList() ?: throw invalidBusinessException("grantedScopes"),
            attemptDate = entity.attemptDate,
            completeDate = entity.completeDate ?: throw invalidBusinessException("completeDate")
        )
    }

    fun toFailedAuthorizeAttempt(entity: AuthorizeAttemptEntity): FailedAuthorizeAttempt {
        return FailedAuthorizeAttempt(
            id = entity.id ?: throw invalidBusinessException("id"),
            authorizationFlowId = entity.authorizationFlowId,
            expirationDate = entity.expirationDate,
            errorDetailsId = entity.errorDetailsId ?: throw invalidBusinessException("errorDetailsId"),
            errorDescriptionId = entity.errorDescriptionId,
            errorValues = entity.errorValues,
            errorDate = entity.errorDate ?: throw invalidBusinessException("errorDate")
        )
    }

    private fun invalidBusinessException(invalidProperty: String): BusinessException {
        return businessExceptionOf(
            detailsId = "mapper.authorize_attempt.invalid_property",
            values = arrayOf("property" to invalidProperty)
        )
    }

    fun toExpiredAuthorizeAttempt(entity: AuthorizeAttemptEntity): FailedAuthorizeAttempt {
        return FailedAuthorizeAttempt(
            id = entity.id!!,
            authorizationFlowId = entity.authorizationFlowId,
            errorDetailsId = "auth.authorize_attempt.validate.expired",
            errorDescriptionId = "description.oauth2.expired",
            errorValues = emptyMap(),
            expirationDate = entity.expirationDate,
            errorDate = entity.expirationDate,
        )
    }

    fun toAuthorizeAttempt(entity: AuthorizeAttemptEntity): AuthorizeAttempt {
        return when {
            entity.errorDate != null -> toFailedAuthorizeAttempt(entity)
            entity.expirationDate.isBefore(LocalDateTime.now()) -> toExpiredAuthorizeAttempt(entity)
            entity.completeDate != null -> toCompletedAuthorizeAttempt(entity)
            else -> toOnGoingAuthorizeAttempt(entity)
        }
    }
}

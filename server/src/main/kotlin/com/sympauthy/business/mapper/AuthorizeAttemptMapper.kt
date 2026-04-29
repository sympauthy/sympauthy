package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.oauth2.*
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
        val (codeChallenge, codeChallengeMethod) = mapCodeChallenge(entity)
        return OnGoingAuthorizeAttempt(
            id = entity.id ?: throw invalidBusinessException("id"),
            authorizationFlowId = entity.authorizationFlowId,
            expirationDate = entity.expirationDate,
            attemptDate = entity.attemptDate,
            clientId = entity.clientId ?: throw invalidBusinessException("clientId"),
            redirectUri = entity.redirectUri ?: throw invalidBusinessException("redirectUri"),
            requestedScopes = entity.requestedScopes.toList(),
            state = entity.state,
            nonce = entity.nonce,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            invitationId = entity.invitationId,
            providerId = entity.providerId,
            providerNonceJsonWebTokenId = entity.providerNonceJsonWebTokenId,
            userId = entity.userId,
            consentedScopes = entity.consentedScopes?.toList(),
            consentedAt = entity.consentedAt,
            consentedBy = entity.consentedBy?.let { ConsentedBy.valueOf(it) },
            mfaPassedDate = entity.mfaPassedDate,
            grantedScopes = entity.grantedScopes?.toList(),
            grantedAt = entity.grantedAt,
            grantedBy = entity.grantedBy?.let { GrantedBy.valueOf(it) },
        )
    }

    fun toCompletedAuthorizeAttempt(entity: AuthorizeAttemptEntity): CompletedAuthorizeAttempt {
        val (codeChallenge, codeChallengeMethod) = mapCodeChallenge(entity)
        return CompletedAuthorizeAttempt(
            id = entity.id ?: throw invalidBusinessException("id"),
            authorizationFlowId = entity.authorizationFlowId,
            expirationDate = entity.expirationDate,
            attemptDate = entity.attemptDate,
            clientId = entity.clientId ?: throw invalidBusinessException("clientId"),
            redirectUri = entity.redirectUri ?: throw invalidBusinessException("redirectUri"),
            requestedScopes = entity.requestedScopes.toList(),
            state = entity.state,
            nonce = entity.nonce,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            userId = entity.userId ?: throw invalidBusinessException("userId"),
            consentedScopes = entity.consentedScopes?.toList() ?: throw invalidBusinessException("consentedScopes"),
            consentedAt = entity.consentedAt ?: throw invalidBusinessException("consentedAt"),
            consentedBy = entity.consentedBy?.let { ConsentedBy.valueOf(it) }
                ?: throw invalidBusinessException("consentedBy"),
            grantedScopes = entity.grantedScopes?.toList() ?: throw invalidBusinessException("grantedScopes"),
            grantedAt = entity.grantedAt ?: throw invalidBusinessException("grantedAt"),
            grantedBy = entity.grantedBy?.let { GrantedBy.valueOf(it) }
                ?: throw invalidBusinessException("grantedBy"),
            completeDate = entity.completeDate ?: throw invalidBusinessException("completeDate"),
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

    /**
     * Map the PKCE fields from the entity, validating consistency:
     * - If `codeChallengeMethod` is present but cannot be decoded, throw.
     * - If only one of `codeChallenge` / `codeChallengeMethod` is present, throw.
     */
    private fun mapCodeChallenge(
        entity: AuthorizeAttemptEntity
    ): Pair<String?, CodeChallengeMethod?> {
        val codeChallenge = entity.codeChallenge
        val rawMethod = entity.codeChallengeMethod

        if (codeChallenge == null && rawMethod == null) {
            return null to null
        }
        if (codeChallenge != null && rawMethod == null) {
            throw invalidBusinessException("codeChallengeMethod")
        }
        if (codeChallenge == null && rawMethod != null) {
            throw invalidBusinessException("codeChallenge")
        }

        val method = CodeChallengeMethod.fromValueOrNull(rawMethod)
            ?: throw invalidBusinessException("codeChallengeMethod")

        return codeChallenge to method
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

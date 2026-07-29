package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.data.model.InteractiveFlowSessionOAuth2Entity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [InteractiveFlowSessionOAuth2Entity] to the [InteractiveFlowSessionOAuth2]
 * business model.
 *
 * If the content of the entity is not valid (e.g. an inconsistent PKCE challenge), an unrecoverable
 * [BusinessException] "mapper.interactive_flow_session.invalid_property" will be thrown.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class InteractiveFlowSessionOAuth2Mapper {

    fun toInteractiveFlowSessionOAuth2(entity: InteractiveFlowSessionOAuth2Entity): InteractiveFlowSessionOAuth2 {
        val (codeChallenge, codeChallengeMethod) = mapCodeChallenge(entity)
        return InteractiveFlowSessionOAuth2(
            sessionId = entity.sessionId,
            clientId = entity.clientId ?: throw invalidBusinessException("clientId"),
            redirectUri = entity.redirectUri ?: throw invalidBusinessException("redirectUri"),
            requestedScopes = entity.requestedScopes.toList(),
            state = entity.state,
            nonce = entity.nonce,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            invitationId = entity.invitationId,
            consentedScopes = entity.consentedScopes?.toList(),
            consentedAt = entity.consentedAt,
            consentedBy = entity.consentedBy?.let { ConsentedBy.valueOf(it) },
            grantedScopes = entity.grantedScopes?.toList(),
            grantedAt = entity.grantedAt,
            grantedBy = entity.grantedBy?.let { GrantedBy.valueOf(it) },
        )
    }

    /**
     * Map the PKCE fields from the entity, validating consistency:
     * - If `codeChallengeMethod` is present but cannot be decoded, throw.
     * - If only one of `codeChallenge` / `codeChallengeMethod` is present, throw.
     */
    private fun mapCodeChallenge(
        entity: InteractiveFlowSessionOAuth2Entity
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
            detailsId = "mapper.interactive_flow_session.invalid_property",
            values = arrayOf("property" to invalidProperty)
        )
    }
}

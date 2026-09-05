package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
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
 * If the content of the entity is not valid (e.g. an inconsistent PKCE challenge), an internal
 * [BusinessException] "mapper.interactive_flow_session_oauth2.invalid_property" is thrown: a row this
 * server wrote and cannot read back is its own failure rather than the caller's.
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
            consentedBy = consentedBy(entity.consentedBy),
            grantedScopes = entity.grantedScopes?.toList(),
            grantedAt = entity.grantedAt,
            grantedBy = grantedBy(entity.grantedBy),
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

    /**
     * How the consentable scopes [consentedBy] names were consented, or null where none were.
     */
    private fun consentedBy(consentedBy: String?): ConsentedBy? {
        return consentedBy?.let {
            try {
                ConsentedBy.valueOf(it)
            } catch (e: IllegalArgumentException) {
                throw invalidBusinessException("consentedBy", e)
            }
        }
    }

    /**
     * How the grantable scopes [grantedBy] names were granted, or null where none were.
     */
    private fun grantedBy(grantedBy: String?): GrantedBy? {
        return grantedBy?.let {
            try {
                GrantedBy.valueOf(it)
            } catch (e: IllegalArgumentException) {
                throw invalidBusinessException("grantedBy", e)
            }
        }
    }

    private fun invalidBusinessException(
        invalidProperty: String,
        cause: Throwable? = null
    ): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.interactive_flow_session_oauth2.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }
}

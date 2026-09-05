package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.TokenRevokedBy
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.util.wireName
import org.mapstruct.AfterMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class AuthenticationTokenMapper {

    @Mapping(target = "allScopes", ignore = true)
    @Mapping(target = "consentedBy", expression = "java(mapConsentedBy(entity.getConsentedBy()))")
    @Mapping(target = "grantedBy", expression = "java(mapGrantedBy(entity.getGrantedBy()))")
    abstract fun toToken(entity: AuthenticationTokenEntity): AuthenticationToken

    fun mapConsentedBy(value: String?): ConsentedBy? = value?.let {
        try {
            ConsentedBy.valueOf(it)
        } catch (e: IllegalArgumentException) {
            throw invalidProperty("consentedBy", e)
        }
    }

    fun mapGrantedBy(value: String?): GrantedBy? = value?.let {
        try {
            GrantedBy.valueOf(it)
        } catch (e: IllegalArgumentException) {
            throw invalidProperty("grantedBy", e)
        }
    }

    /**
     * The token type [value] names, refusing the row where it names none.
     */
    fun mapType(value: String): AuthenticationTokenType {
        return try {
            AuthenticationTokenType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw invalidProperty("type", e)
        }
    }

    /**
     * Who revoked the token [value] names, or null where it is not revoked.
     */
    fun mapRevokedBy(value: String?): TokenRevokedBy? = value?.let {
        try {
            TokenRevokedBy.valueOf(it)
        } catch (e: IllegalArgumentException) {
            throw invalidProperty("revokedBy", e)
        }
    }

    /**
     * The failure thrown when the column [invalidProperty] holds a value no enum of the model names.
     *
     * It is separate from the coherence codes below, which name a row whose columns each read back but
     * contradict each other; this one names a column that does not read back at all.
     */
    private fun invalidProperty(invalidProperty: String, cause: Throwable): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.authentication_token.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }

    @AfterMapping
    protected fun validateTokenCoherence(@MappingTarget token: AuthenticationToken) {
        when (token.grantType) {
            GrantType.CLIENT_CREDENTIALS.wireName -> {
                if (token.userId != null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.client_credentials.invalid_user_id",
                        "grantType" to token.grantType
                    )
                }
                if (token.sessionId != null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.client_credentials.invalid_session_id",
                        "grantType" to token.grantType
                    )
                }
                requireNoActorTokenId(token)
            }

            GrantType.TOKEN_EXCHANGE.wireName -> {
                // Act-as tokens carry a target user but are not tied to an authorization flow.
                if (token.userId == null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.missing_user_id",
                        "grantType" to token.grantType
                    )
                }
                if (token.sessionId != null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.token_exchange.invalid_session_id",
                        "grantType" to token.grantType
                    )
                }
                // An act-as token is always issued by exchanging a subject_token.
                if (token.actorTokenId == null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.token_exchange.missing_actor_token_id",
                        "grantType" to token.grantType
                    )
                }
            }

            else -> {
                if (token.userId == null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.missing_user_id",
                        "grantType" to token.grantType
                    )
                }
                if (token.sessionId == null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.missing_session_id",
                        "grantType" to token.grantType
                    )
                }
                requireNoActorTokenId(token)
            }
        }
    }

    /**
     * The [AuthenticationToken.actorTokenId] must only be set for tokens issued through token exchange.
     */
    private fun requireNoActorTokenId(token: AuthenticationToken) {
        if (token.actorTokenId != null) {
            throw internalBusinessExceptionOf(
                "mapper.authentication_token.invalid_actor_token_id",
                "grantType" to token.grantType
            )
        }
    }
}

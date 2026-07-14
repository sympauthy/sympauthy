package com.sympauthy.business.mapper

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.GrantedBy
import com.sympauthy.data.model.AuthenticationTokenEntity
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

    fun mapConsentedBy(value: String?): ConsentedBy? = value?.let { ConsentedBy.valueOf(it) }

    fun mapGrantedBy(value: String?): GrantedBy? = value?.let { GrantedBy.valueOf(it) }

    @AfterMapping
    protected fun validateTokenCoherence(@MappingTarget token: AuthenticationToken) {
        when (token.grantType) {
            "client_credentials" -> {
                if (token.userId != null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.client_credentials.invalid_user_id",
                        "grantType" to token.grantType
                    )
                }
                if (token.authorizeAttemptId != null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.client_credentials.invalid_authorize_attempt_id",
                        "grantType" to token.grantType
                    )
                }
                requireNoActorTokenId(token)
            }

            GrantType.TOKEN_EXCHANGE.value -> {
                // Act-as tokens carry a target user but are not tied to an authorization flow.
                if (token.userId == null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.missing_user_id",
                        "grantType" to token.grantType
                    )
                }
                if (token.authorizeAttemptId != null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.token_exchange.invalid_authorize_attempt_id",
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
                if (token.authorizeAttemptId == null) {
                    throw internalBusinessExceptionOf(
                        "mapper.authentication_token.missing_authorize_attempt_id",
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

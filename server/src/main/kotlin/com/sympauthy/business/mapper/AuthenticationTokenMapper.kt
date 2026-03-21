package com.sympauthy.business.mapper

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
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
            }
        }
    }
}

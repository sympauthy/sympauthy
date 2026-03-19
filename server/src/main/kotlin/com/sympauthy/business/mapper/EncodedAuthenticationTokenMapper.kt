package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.data.model.AuthenticationTokenEntity
import org.mapstruct.Mapper

@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class EncodedAuthenticationTokenMapper {

    fun toEncodedAuthenticationToken(
        entity: AuthenticationTokenEntity,
        token: String
    ): EncodedAuthenticationToken {
        return EncodedAuthenticationToken(
            id = entity.id!!,
            type = AuthenticationTokenType.valueOf(entity.type),
            token = token,
            scopes = entity.grantedScopes.toList() + entity.consentedScopes.toList() + entity.clientScopes.toList(),
            issueDate = entity.issueDate,
            expirationDate = entity.expirationDate
        )
    }
}

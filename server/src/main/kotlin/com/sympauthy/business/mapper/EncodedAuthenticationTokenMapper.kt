package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.oauth2.EncodedAuthenticationToken
import com.sympauthy.data.model.AuthenticationTokenEntity
import org.mapstruct.Mapper

/**
 * Handle the mapping from the [AuthenticationTokenEntity] and the token it was encoded into to the
 * [EncodedAuthenticationToken] business model.
 *
 * If the row holds no identifier, or a type [AuthenticationTokenType] does not name, an internal
 * [BusinessException] "mapper.encoded_authentication_token.invalid_property" is thrown: a row this
 * server wrote and cannot read back is its own failure rather than the caller's.
 */
@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class EncodedAuthenticationTokenMapper {

    fun toEncodedAuthenticationToken(
        entity: AuthenticationTokenEntity,
        token: String
    ): EncodedAuthenticationToken {
        return EncodedAuthenticationToken(
            id = entity.id ?: throw invalidBusinessException("id"),
            type = type(entity.type),
            token = token,
            scopes = entity.grantedScopes.toList() + entity.consentedScopes.toList() + entity.clientScopes.toList(),
            issueDate = entity.issueDate,
            expirationDate = entity.expirationDate
        )
    }

    /**
     * The token type [type] names, refusing the row where it names none.
     */
    private fun type(type: String): AuthenticationTokenType {
        return try {
            AuthenticationTokenType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            throw invalidBusinessException("type", e)
        }
    }

    private fun invalidBusinessException(
        invalidProperty: String,
        cause: Throwable? = null
    ): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.encoded_authentication_token.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }
}

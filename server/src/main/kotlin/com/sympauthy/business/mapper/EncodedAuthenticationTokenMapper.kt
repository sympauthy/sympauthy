package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.token.AuthenticationTokenType
import com.sympauthy.business.model.token.EncodedAuthenticationToken
import com.sympauthy.data.model.AuthenticationTokenEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [AuthenticationTokenEntity] and the token it was encoded into to the
 * [EncodedAuthenticationToken] business model.
 *
 * If the row holds no identifier, or a type [AuthenticationTokenType] does not name, an internal
 * [BusinessException] "mapper.encoded_authentication_token.invalid_property" is thrown: a row this
 * server wrote and cannot read back is its own failure rather than the caller's.
 */
@Singleton
class EncodedAuthenticationTokenMapper {

    /**
     * The token [entity] describes, encoded as [token] and issued under [grant], whose scopes it carries.
     */
    fun toEncodedAuthenticationToken(
        entity: AuthenticationTokenEntity,
        grant: Grant,
        token: String
    ): EncodedAuthenticationToken {
        return EncodedAuthenticationToken(
            id = entity.id ?: throw invalidBusinessException("id"),
            type = type(entity.type),
            token = token,
            scopes = grant.allScopes,
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

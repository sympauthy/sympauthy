package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.AuthenticationTokenType
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.util.enumValueOfWireNameOrNull
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AuthenticationTokenMapper {

    /**
     * The token [entity] describes, issued under [grant], which is the one the row names or null where it is
     * gone.
     *
     * Throws `mapper.authentication_token.invalid_property`, internal, where [grant] is null or is not the one
     * the row names: a grant is deleted only with its tokens, so a row naming one that is not there is this
     * server's failure. Throws `mapper.authentication_token.incoherent_grant`, internal, where the token could
     * not have been issued under [grant], as [validateGrantCoherence] says.
     */
    fun toToken(entity: AuthenticationTokenEntity, grant: Grant?): AuthenticationToken {
        if (grant == null || entity.grantId != grant.id) {
            throw invalidProperty("grantId")
        }
        val token = AuthenticationToken(
            id = entity.id ?: throw invalidProperty("id"),
            type = mapType(entity.type),
            grant = grant,
            grantType = mapGrantType(entity.grantType),
            dpopJkt = entity.dpopJkt,
            issueDate = entity.issueDate,
            expirationDate = entity.expirationDate,
        )
        validateGrantCoherence(token)
        return token
    }

    /**
     * Refuse a [token] that could not have been issued under its grant.
     *
     * A token is issued by the grant type that opened its grant, or by a refresh of a grant an authorization
     * opened; and only such a grant holds a refresh token or an id token, since a `client_credentials` grant
     * and a token exchange are each answered with an access token alone.
     */
    private fun validateGrantCoherence(token: AuthenticationToken) {
        val openedBy = token.grant.grantType
        val issuedBy = token.grantType
        val authorization = openedBy == GrantType.AUTHORIZATION_CODE
        val coherent = (issuedBy == openedBy || (authorization && issuedBy == GrantType.REFRESH_TOKEN)) &&
            (authorization || token.type == AuthenticationTokenType.ACCESS)
        if (!coherent) {
            throw internalBusinessExceptionOf(
                "mapper.authentication_token.incoherent_grant",
                "type" to token.type.name,
                "issuedBy" to issuedBy.wireName,
                "openedBy" to openedBy.wireName
            )
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
     * The grant type [value] names as it is published, refusing the row where it names none.
     */
    fun mapGrantType(value: String): GrantType {
        return enumValueOfWireNameOrNull<GrantType>(value) ?: throw invalidProperty("grantType")
    }

    /**
     * The failure thrown when the column [invalidProperty] is missing or holds a value no enum of the
     * model names.
     */
    private fun invalidProperty(invalidProperty: String, cause: Throwable? = null): BusinessException {
        return internalBusinessExceptionOf(
            detailsId = "mapper.authentication_token.invalid_property",
            throwable = cause,
            values = arrayOf("property" to invalidProperty)
        )
    }
}

package com.sympauthy.business.mapper

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.data.model.AuthorizationCodeEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [AuthorizationCodeEntity] to the [AuthorizationCode] business model.
 *
 * A code whose challenge method is not one this server knows throws an internal
 * "mapper.authorization_code.invalid_property": a row this server wrote and cannot read back is its own
 * failure rather than the caller's.
 */
@Singleton
class AuthorizationCodeMapper {

    fun toAuthorizationCode(entity: AuthorizationCodeEntity): AuthorizationCode {
        return AuthorizationCode(
            grantId = entity.grantId,
            code = entity.code,
            codeChallenge = entity.codeChallenge,
            codeChallengeMethod = CodeChallengeMethod.fromValueOrNull(entity.codeChallengeMethod)
                ?: throw internalBusinessExceptionOf(
                    "mapper.authorization_code.invalid_property",
                    "property" to "codeChallengeMethod"
                ),
            nonce = entity.nonce,
            creationDate = entity.creationDate,
            expirationDate = entity.expirationDate,
        )
    }
}

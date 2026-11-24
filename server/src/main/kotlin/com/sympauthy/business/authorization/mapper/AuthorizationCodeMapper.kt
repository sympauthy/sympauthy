package com.sympauthy.business.authorization.mapper

import com.sympauthy.business.authorization.model.AuthorizationCode
import com.sympauthy.business.authorization.model.CodeChallengeMethod
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.data.model.AuthorizationCodeEntity
import jakarta.inject.Singleton

/**
 * Handle the mapping from the [AuthorizationCodeEntity] to the [AuthorizationCode] business model.
 *
 * A code whose challenge method is not one this server knows throws an internal
 * "mapper.authorization_code.invalid_property".
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

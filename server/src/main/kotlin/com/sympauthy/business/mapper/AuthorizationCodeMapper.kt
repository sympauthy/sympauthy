package com.sympauthy.business.mapper

import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.data.model.AuthorizationCodeEntity
import jakarta.inject.Singleton

@Singleton
class AuthorizationCodeMapper {

    fun toAuthorizationCode(entity: AuthorizationCodeEntity): AuthorizationCode {
        return AuthorizationCode(
            sessionId = entity.sessionId,
            code = entity.code,
            creationDate = entity.creationDate,
            expirationDate = entity.expirationDate,
            grantId = entity.grantId,
        )
    }
}

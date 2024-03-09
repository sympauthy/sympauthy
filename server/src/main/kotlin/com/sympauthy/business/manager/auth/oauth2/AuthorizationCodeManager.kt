package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.RandomGenerator
import com.sympauthy.business.mapper.AuthorizationCodeMapper
import com.sympauthy.business.model.oauth2.AuthorizationCode
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.repository.AuthorizationCodeRepository
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.LocalDateTime

/**
 * Manager in charge of generating the authorization code that will be exchange by the client against
 * the access, refresh and id tokens.
 */
@Singleton
class AuthorizationCodeManager(
    @Inject private val authorizeCodeRepository: AuthorizationCodeRepository,
    @Inject private val authorizationCodeMapper: AuthorizationCodeMapper,
    @Inject private val randomGenerator: RandomGenerator
) {

    suspend fun generateCode(
        authorizeAttempt: AuthorizeAttempt
    ): AuthorizationCode {
        val entity = AuthorizationCodeEntity(
            attemptId = authorizeAttempt.id,
            code = randomGenerator.generateAndEncodeToBase64(),
            creationDate = LocalDateTime.now(),
            // We copy the expiration to simplify the cleanup code.
            expirationDate = authorizeAttempt.expirationDate
        )
        return try {
            authorizeCodeRepository.save(entity)
                .let(authorizationCodeMapper::toAuthorizationCode)
        } catch (e: R2dbcDataIntegrityViolationException) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "code.already_generated", "description.oauth2.replay")
        }
    }

    suspend fun deleteCode(code: String) {
        authorizeCodeRepository.deleteByCode(code)
    }
}

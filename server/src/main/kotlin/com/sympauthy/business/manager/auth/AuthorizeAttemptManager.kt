package com.sympauthy.business.manager.auth

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.AuthorizeAttemptMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.data.model.AuthorizeAttemptEntity
import com.sympauthy.data.repository.AuthorizeAttemptRepository
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.localizedExceptionOf
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.time.LocalDateTime
import java.util.*

/**
 * Manager in charge of the lifecycle of an authorization attempt.
 * It provides methods to:
 * - create an authorization attempt after validation.
 * - verify the state of an authorization attempt.
 * - modify the attempt: user id, granted scopes, error.
 *
 * Note: For separation of concerns, this manager does not handle any logic of the authorization flow. Those logics
 * are handled by managers in the flow package.
 */
@Singleton
class AuthorizeAttemptManager(
    @Inject private val jwtManager: JwtManager,
    @Inject private val authorizeAttemptRepository: AuthorizeAttemptRepository,
    @Inject private val authorizeAttemptMapper: AuthorizeAttemptMapper
) {

    suspend fun newAuthorizeAttempt(
        client: Client,
        authorizationFlow: AuthorizationFlow,
        clientState: String? = null,
        clientNonce: String? = null,
        uncheckedScopes: List<Scope>? = null,
        uncheckedRedirectUri: URI? = null,
    ): AuthorizeAttempt {
        val scopes = getAllowedScopesForClient(client, uncheckedScopes)
        val redirectUri = checkRedirectUri(client, uncheckedRedirectUri)
        checkIsExistingAttemptWithState(clientState)

        val entity = AuthorizeAttemptEntity(
            clientId = client.id,
            authorizationFlowId = authorizationFlow.id,
            redirectUri = redirectUri.toString(),
            requestedScopes = scopes.map(Scope::scope).toTypedArray(),
            state = clientState,
            nonce = clientNonce,

            attemptDate = LocalDateTime.now(),
            expirationDate = LocalDateTime.now().plusMinutes(15) // TODO: Add to advanced config
        )
        authorizeAttemptRepository.save(entity)

        return authorizeAttemptMapper.toAuthorizeAttempt(entity)
    }

    /**
     * Return a list containing only the scope allowed by the [client].
     * If no scope are provided, then it returns the list of default scopes for the client.
     * If all scopes are filtered or if default scope are empty, then throws a [LocalizedException].
     */
    internal fun getAllowedScopesForClient(
        client: Client,
        uncheckedScopes: List<Scope>?
    ): List<Scope> {
        val scopes = when {
            uncheckedScopes.isNullOrEmpty() -> client.defaultScopes
            client.allowedScopes != null -> {
                val allowedScopes = client.allowedScopes.map(Scope::scope)
                uncheckedScopes.filter { allowedScopes.contains(it.scope) }
            }

            else -> uncheckedScopes
        }
        if (scopes.isNullOrEmpty()) {
            throw localizedExceptionOf("authorize.scope.missing")
        }
        return scopes
    }

    internal fun checkRedirectUri(
        client: Client,
        uncheckedRedirectUri: URI?
    ): URI {
        // TODO: check redirect uri is valid for client.
        return uncheckedRedirectUri!!
    }

    internal suspend fun checkIsExistingAttemptWithState(
        state: String?
    ) {
        val existingAttempt = state?.let {
            authorizeAttemptRepository.findByState(it)
        }
        if (existingAttempt != null) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "authorize.existing_state", "description.oauth2.replay")
        }
    }

    suspend fun encodeState(authorizeAttempt: AuthorizeAttempt): String {
        return jwtManager.create(STATE_KEY_NAME) {
            withKeyId(STATE_KEY_NAME)
            withSubject(authorizeAttempt.id.toString())
        }
    }

    /**
     * Return a [SuccessVerifyEncodedStateResult] containing the [AuthorizeAttempt] that created the [state]
     * after verifying the [state] has not been tempered with.
     * Otherwise, return a [FailedVerifyEncodedStateResult] with the appropriate error details.
     */
    suspend fun verifyEncodedState(state: String?): VerifyEncodedStateResult {
        if (state.isNullOrBlank()) {
            return FailedVerifyEncodedStateResult(
                detailsId = "auth.authorize_attempt.validate.missing",
                descriptionId = "description.oauth2.invalid_state"
            )
        }
        val jwt = jwtManager.decodeAndVerifyOrNull(STATE_KEY_NAME, state) ?: return FailedVerifyEncodedStateResult(
            detailsId = "auth.authorize_attempt.validate.wrong_signature",
            descriptionId = "description.oauth2.invalid_state"
        )
        val attemptId = try {
            UUID.fromString(jwt.subject)
        } catch (e: IllegalArgumentException) {
            return FailedVerifyEncodedStateResult(
                detailsId = "auth.authorize_attempt.validate.invalid_subject",
                descriptionId = "description.oauth2.invalid_state"
            )
        }
        val authorizeAttempt = authorizeAttemptRepository.findById(attemptId)
            ?.let(authorizeAttemptMapper::toAuthorizeAttempt)
        if (authorizeAttempt == null || authorizeAttempt.expired) {
            // If the attempt is missing in DB, most likely a cron cleaned it.
            return FailedVerifyEncodedStateResult(
                detailsId = "auth.authorize_attempt.validate.expired",
                descriptionId = "description.oauth2.expired"
            )
        }
        if (authorizeAttempt.errorDetailsId != null) {
            return FailedVerifyEncodedStateResult(
                detailsId = authorizeAttempt.errorDetailsId,
                descriptionId = authorizeAttempt.errorDescriptionId,
                values = authorizeAttempt.errorValues
            )
        }
        return SuccessVerifyEncodedStateResult(authorizeAttempt)
    }

    /**
     * Associate the user that have been authenticated to its [AuthorizeAttempt].
     */
    suspend fun setAuthenticatedUserId(
        authorizeAttempt: AuthorizeAttempt,
        userId: UUID
    ): AuthorizeAttempt {
        authorizeAttemptRepository.updateUserId(
            id = authorizeAttempt.id,
            userId = userId
        )
        return authorizeAttempt.copy(
            userId = userId
        )
    }

    /**
     * Set and save the list of scopes that have been granted to the user.
     * If the [AuthorizeAttempt.grantedScopes] were already determined, return immediately.
     */
    suspend fun setGrantedScopes(
        authorizeAttempt: AuthorizeAttempt,
    ): AuthorizeAttempt {
        return if (authorizeAttempt.grantedScopes != null) {
            authorizeAttemptRepository.updateGrantedScopes(
                id = authorizeAttempt.id,
                grantedScopes = authorizeAttempt.grantedScopes
            )
            authorizeAttempt.copy(
                grantedScopes = authorizeAttempt.grantedScopes
            )
        } else authorizeAttempt
    }

    /**
     * Set and save the error if it is non-recoverable to prevent further usage of the [authorizeAttempt].
     */
    suspend fun setErrorIfNonRecoverable(
        authorizeAttempt: AuthorizeAttempt,
        error: BusinessException
    ): AuthorizeAttempt {
        if (error.recoverable) return authorizeAttempt
        if (authorizeAttempt.errorDetailsId != null) return authorizeAttempt
        val errorDate = LocalDateTime.now()
        authorizeAttemptRepository.updateError(
            id = authorizeAttempt.id,
            errorDate = errorDate,
            errorDetailsId = error.detailsId,
            errorDescriptionId = error.descriptionId,
            errorValues = error.values
        )
        return authorizeAttempt.copy(
            errorDate = errorDate,
            errorDetailsId = error.detailsId,
            errorDescriptionId = error.descriptionId,
            errorValues = error.values
        )
    }

    suspend fun findByCode(code: String): AuthorizeAttempt? {
        val authorizeAttempt = authorizeAttemptRepository.findByCode(code)
            ?.let(authorizeAttemptMapper::toAuthorizeAttempt)
        return if (authorizeAttempt?.expired == false) {
            authorizeAttempt
        } else null
    }

    companion object {
        /**
         * Name of the cryptographic key used to sign the state.
         */
        const val STATE_KEY_NAME = "state"
    }
}

sealed class VerifyEncodedStateResult

class SuccessVerifyEncodedStateResult(val authorizeAttempt: AuthorizeAttempt) : VerifyEncodedStateResult()

class FailedVerifyEncodedStateResult(
    val detailsId: String,
    val descriptionId: String?,
    val values: Map<String, String>? = null
) : VerifyEncodedStateResult()

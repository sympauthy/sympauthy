package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
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

@Singleton
class AuthorizeManager(
    @Inject private val authorizationFlowManager: AuthorizationFlowManager,
    @Inject private val authorizeAttemptRepository: AuthorizeAttemptRepository,
    @Inject private val jwtManager: JwtManager,
    @Inject private val authorizeAttemptMapper: AuthorizeAttemptMapper
) {

    suspend fun newAuthorizeAttempt(
        client: Client,
        clientState: String? = null,
        clientNonce: String? = null,
        uncheckedScopes: List<Scope>? = null,
        uncheckedRedirectUri: URI? = null,
    ): AuthorizeAttemptResult {
        val scopes = getAllowedScopesForClient(client, uncheckedScopes)
        val redirectUri = checkRedirectUri(client, uncheckedRedirectUri)
        checkIsExistingAttemptWithState(clientState)

        val authorizationFlow = client.authorizationFlow ?: authorizationFlowManager.defaultAuthorizationFlow

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

        return AuthorizeAttemptResult(
            authorizeAttempt = authorizeAttemptMapper.toAuthorizeAttempt(entity),
            authorizationFlow = authorizationFlow
        )
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
     * Return the [AuthorizeAttempt] that created the [state] after verifying the [state] has not been tempered with.
     */
    suspend fun verifyEncodedState(state: String?): AuthorizeAttempt {
        if (state.isNullOrBlank()) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "authorize.state.missing")
        }
        val jwt = jwtManager.decodeAndVerifyOrNull(STATE_KEY_NAME, state) ?: throw oauth2ExceptionOf(
            INVALID_REQUEST, "authorize.state.wrong_signature", "description.oauth2.invalid_state"
        )
        val attemptId = try {
            UUID.fromString(jwt.subject)
        } catch (e: IllegalArgumentException) {
            throw oauth2ExceptionOf(
                INVALID_REQUEST, "authorize.state.invalid_subject", "description.oauth2.invalid_state"
            )
        }
        val authorizeAttempt = authorizeAttemptRepository.findById(attemptId)
            ?.let(authorizeAttemptMapper::toAuthorizeAttempt)
        if (authorizeAttempt == null || authorizeAttempt.expired) {
            // If the attempt is missing in DB, most likely a cron cleaned it.
            throw oauth2ExceptionOf(
                INVALID_REQUEST, "authorize.state.expired", "description.oauth2.expired"
            )
        }
        return authorizeAttempt
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
     * Pass the [AuthorizeAttempt.requestedScopes] through the chain of scope granting mtehod.
     *
     * The scopes that have not been granted or declined by an higher order method will be passed to
     * the next one and the process continue until there is no more scope or no more method.
     *
     * The supported scope granting methods are:
     * - call the third-party authorization delegation API configured for the client.
     * - scope granting rules will be applied.
     * - decline all.
     *
     * @see [com.sympauthy.business.manager.rule.ScopeGrantingRuleManager.applyScopeGrantingRules]
     */
    suspend fun grantScopes(
        authorizeAttempt: AuthorizeAttempt,
    ): AuthorizeAttempt {
        TODO("FIXME")
        return authorizeAttempt
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

data class AuthorizeAttemptResult(
    val authorizeAttempt: AuthorizeAttempt,
    val authorizationFlow: AuthorizationFlow
)

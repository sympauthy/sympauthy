package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.auth.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.AuthorizationFlow.Companion.DEFAULT_AUTHORIZATION_FLOW_ID
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.security.state
import com.sympauthy.view.DefaultAuthorizationFlowController.Companion.USER_FLOW_ENDPOINT
import io.micronaut.http.HttpStatus
import io.micronaut.http.uri.UriBuilder
import io.micronaut.security.authentication.Authentication
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager providing utility methods supporting the lifecycle of web-based authorization flows.
 */
@Singleton
class WebAuthorizationFlowManager(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val claimValidationManager: AuthorizationFlowClaimValidationManager,
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    /**
     * The default web authentication flow is hardcoded since it is bundled with this authorization server.
     */
    val defaultAuthorizationFlow: WebAuthorizationFlow by lazy {
        val builder = uncheckedUrlsConfig.orThrow().root
            .let(UriBuilder::of)
            .path(USER_FLOW_ENDPOINT)
        WebAuthorizationFlow(
            id = DEFAULT_AUTHORIZATION_FLOW_ID,
            signInUri = builder.path("sign-in").build(),
            collectClaimsUri = builder.path("claims/edit").build(),
            validateClaimsUri = builder.path("claims/validate").build(),
            errorUri = builder.path("error").build(),
        )
    }

    /**
     * Return the [AuthorizationFlow] identified by [id] or null.
     */
    fun findByIdOrNull(id: String): AuthorizationFlow? {
        if (id == DEFAULT_AUTHORIZATION_FLOW_ID) {
            return defaultAuthorizationFlow
        }
        return authorizationFlowsConfig.orThrow().flows
            .firstOrNull { it.id == id }
    }


    /**
     * Retrieve the [AuthorizeAttempt] using the state.
     * Verify the following:
     * - the [AuthorizeAttempt] has not failed nor expired.
     * - the [AuthorizeAttempt] is associated with a [WebAuthorizationFlow].
     *
     * If all of those conditions are met, the [method] will be executed with the [AuthorizeAttempt]
     * and [WebAuthorizationFlow] as parameters. Otherwise, a [BusinessException] will be thrown.
     *
     * If the method throws a non-recoverable exception, it will be saved in the [AuthorizeAttempt] to prevent further
     * usage.
     */
    suspend fun <T> extractFromStateVerifyThenRun(
        state: String?,
        method: suspend (AuthorizeAttempt, WebAuthorizationFlow) -> T
    ): T {
        val verifyResult = authorizeAttemptManager.verifyEncodedState(state)
        val authorizeAttempt = when (verifyResult) {
            is SuccessVerifyEncodedStateResult -> verifyResult.authorizeAttempt
            is FailedVerifyEncodedStateResult -> {
                throw businessExceptionOf(
                    detailsId = verifyResult.detailsId,
                    descriptionId = verifyResult.descriptionId,
                    recommendedStatus = HttpStatus.BAD_REQUEST,
                )
            }
        }
        val flow = verifyIsWebFlow(authorizeAttempt)
        return try {
            method(authorizeAttempt, flow)
        } catch (e: BusinessException) {
            authorizeAttemptManager.setErrorIfNonRecoverable(
                authorizeAttempt = authorizeAttempt,
                error = e,
            )
            throw e
        }
    }

    /**
     * Retrieve the state from the [Authentication] then call [extractFromStateVerifyThenRun].
     */
    suspend fun <T> extractFromAuthenticationAndVerifyThenRun(
        authentication: Authentication,
        method: suspend (AuthorizeAttempt, WebAuthorizationFlow) -> T
    ): T = extractFromStateVerifyThenRun(
        state = authentication.state,
        method = method
    )

    /**
     * Check and return the authorization flow associated with the [authorizeAttempt] if it is a [WebAuthorizationFlow].
     * Otherwise, throw a [BusinessException].
     */
    fun verifyIsWebFlow(
        authorizeAttempt: AuthorizeAttempt
    ): WebAuthorizationFlow {
        val flow = authorizeAttempt.authorizationFlowId?.let(this::findByIdOrNull)
        if (flow !is WebAuthorizationFlow) {
            TODO("Put proper exception to verify the flow")
        }
        return flow
    }

    /**
     *
     */
    suspend fun completeAuthorizationFlowOrRedirect(
        user: User,
        collectedClaims: List<CollectedClaim>
    ): AuthorizationFlowResult {
        val missingRequiredClaims = !collectedClaimManager.areAllRequiredClaimCollected(collectedClaims)
        val missingMediaForClaimValidation = claimValidationManager.getReasonsToSendValidationCode(collectedClaims)
            .map(ValidationCodeReason::media)
            .distinct()

        // FIXME handle granting scopes

        return AuthorizationFlowResult(
            user = user,
            missingRequiredClaims = missingRequiredClaims,
            missingMediaForClaimValidation = missingMediaForClaimValidation,
        )
    }
}

/**
 * The result of the authorization flow.
 */
data class AuthorizationFlowResult(
    /**
     * The user that has been authentication during the authentication flow.
     */
    val user: User,
    /**
     * True if we are missing some required claims from the end-user and they must be collected by the client.
     */
    val missingRequiredClaims: Boolean,
    /**
     * List of media we must send a validation code too according to the claims collected from the end-user.
     */
    val missingMediaForClaimValidation: List<ValidationCodeMedia>,
) {

    /**
     * True if the authorization is complete and the user can be redirected to the client.
     */
    val complete: Boolean = listOf(
        missingRequiredClaims,
        missingMediaForClaimValidation.isNotEmpty(),
    ).none { it }
}

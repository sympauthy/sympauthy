package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.auth.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.AuthorizationFlow.Companion.DEFAULT_AUTHORIZATION_FLOW_ID
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
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
    @Inject private val authorizationFlowManager: AuthorizationFlowManager,
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val claimValidationManager: WebAuthorizationFlowClaimValidationManager,
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    /**
     * Note: The default web authentication flow is hardcoded since it is bundled with this authorization server.
     */
    val defaultWebAuthorizationFlow: WebAuthorizationFlow by lazy {
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
     * Return the [WebAuthorizationFlow] identified by [id] or null.
     */
    fun findByIdOrNull(id: String): WebAuthorizationFlow? {
        if (id == DEFAULT_AUTHORIZATION_FLOW_ID) {
            return defaultWebAuthorizationFlow
        }
        return authorizationFlowsConfig.orThrow().flows
            .filterIsInstance<WebAuthorizationFlow>()
            .firstOrNull { it.id == id }
    }

    /**
     * Return the [WebAuthorizationFlow] of throw a non-recoverable [BusinessException] if not found.
     */
    fun findById(id: String?): WebAuthorizationFlow {
        return id?.let(this::findByIdOrNull) ?: throw businessExceptionOf(
            detailsId = "flow.web.invalid_flow",
            recommendedStatus = HttpStatus.BAD_REQUEST,
            values = arrayOf("flowId" to (id ?: ""))
        )
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

        return try {
            val flow = this.findById(authorizeAttempt.authorizationFlowId)
            method(authorizeAttempt, flow)
        } catch (e: BusinessException) {
            authorizeAttemptManager.markAsFailedIfNotRecoverable(
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
     * Return the status of the [authorizeAttempt] if the end-user is going through a web authorization flow.
     */
    suspend fun getStatus(
        authorizeAttempt: AuthorizeAttempt
    ): WebAuthorizationFlowStatus {
        val collectedClaims = collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt)

        val missingUser = authorizeAttempt.userId == null
        val missingRequiredClaims = !collectedClaimManager.areAllRequiredClaimCollected(collectedClaims)
        val missingMediaForClaimValidation = claimValidationManager.getReasonsToSendValidationCode(collectedClaims)
            .map(ValidationCodeReason::media)
            .distinct()

        return WebAuthorizationFlowStatus(
            missingUser = missingUser,
            missingRequiredClaims = missingRequiredClaims,
            missingMediaForClaimValidation = missingMediaForClaimValidation
        )
    }

    /**
     * Retrieves the current status of the provided [authorizeAttempt] and completes the authorization flow
     * if it is determined to be complete.
     *
     * This method internally calls [getStatus] to evaluate the state of the authorization flow. If the
     * returned status indicates that the flow is complete, it proceeds to invoke
     * [AuthorizationFlowManager.completeAuthorization] to finalize the process.
     * The resulting status is then returned.
     */
    suspend fun getStatusAndCompleteIfNecessary(
        authorizeAttempt: AuthorizeAttempt,
    ): WebAuthorizationFlowStatus {
        val status = getStatus(authorizeAttempt)
        if (status.complete) {
            authorizationFlowManager.completeAuthorization(authorizeAttempt)
        }
        return status
    }
}


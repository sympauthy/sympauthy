package com.sympauthy.api.controller.flow.util

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.auth.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.WebAuthorizationFlowManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.User
import io.micronaut.http.HttpStatus
import io.micronaut.security.authentication.Authentication
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

/**
 * Utility class for controller providing APIs for web authorization flows.
 *
 * It provides utility methods for controller to retrieve the following:
 * - the [OnGoingAuthorizeAttempt] associated to the state in the [Authentication].
 * - the [User] associated to the [OnGoingAuthorizeAttempt].
 * - the [WebAuthorizationFlow] associated to the [OnGoingAuthorizeAttempt].
 */
@Singleton
class WebAuthorizationFlowControllerUtil(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val userManager: UserManager,
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder
) {

    /**
     * Call the [run] function with the [OnGoingAuthorizeAttempt] and [WebAuthorizationFlow] associated to the [state].
     * Then run and return the result of the [run] function.
     */
    suspend fun <Resource> fetchOnGoingAttemptThenRun(
        state: String?,
        run: suspend (OnGoingAuthorizeAttempt, WebAuthorizationFlow) -> Resource
    ): Resource {
        val authorizeAttempt = fetchAuthorizeAttempt(state)
        val flow = webAuthorizationFlowManager.findById(authorizeAttempt.authorizationFlowId)
        val onGoingAuthorizeAttempt = (authorizeAttempt as? OnGoingAuthorizeAttempt) ?: throw httpExceptionOf(
            status = HttpStatus.BAD_REQUEST,
            detailsId = "ctrl.flow.not_ongoing",
        )
        return run(onGoingAuthorizeAttempt, flow)
    }

    /**
     * Call the [run] function with the [OnGoingAuthorizeAttempt] and [WebAuthorizationFlow] associated to the [state].
     * Then run and return the result of the [run] function.
     */
    suspend fun <Resource> fetchOnGoingAttemptWithUserThenRun(
        state: String?,
        run: suspend (OnGoingAuthorizeAttempt, WebAuthorizationFlow, User) -> Resource
    ): Resource {
        return fetchOnGoingAttemptThenRun(state) { onGoingAuthorizeAttempt, flow ->
            val user = userManager.findById(onGoingAuthorizeAttempt.userId)
            run(onGoingAuthorizeAttempt, flow, user)
        }
    }

    /**
     * Call the [run] function with the [OnGoingAuthorizeAttempt] and [WebAuthorizationFlow] associated to the [state].
     * Then run and return the result of:
     * - [mapRedirectUriToResource] if the end-user is expected to be redirected to a different step.
     * - [mapResultToResource] if the end-user is expected to perform an action to complete the step.
     *
     * If the operation is only meant to redirect the end-user to a different step, the [mapResultToResource] parameter may be set to null.
     *
     * It expects the [run] to either return a value if the [User] is expected to perform an action to complete the step
     * or return null if the [User] is expected to be redirected to a different step.
     *
     * [BusinessException] thrown by the [run] function will be caught and handled differently:
     * - if recoverable, the [BusinessException] will be simply thrown to be handled by the exception handler.
     * - if unrecoverable, the [AuthorizeAttempt] will be marked as failed and the end-user will be redirected to the error page.
     */
    suspend fun <Result, FlowResource> fetchOnGoingAttemptThenRunAndRedirect(
        state: String?,
        run: suspend (OnGoingAuthorizeAttempt, WebAuthorizationFlow) -> Result?,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
        mapResultToResource: (suspend (Result) -> FlowResource)? = null
    ): FlowResource {
        val authorizeAttempt = fetchAuthorizeAttempt(state)
        val onGoingAuthorizeAttempt = authorizeAttempt as? OnGoingAuthorizeAttempt

        val flow = try {
            webAuthorizationFlowManager.findById(authorizeAttempt.authorizationFlowId)
        } catch (_: BusinessException) {
            // Redirect to the error page of the default flow since the information on the exact flow is missing.
            val redirectUri = redirectUriBuilder.getErrorUri(
                authorizeAttempt = authorizeAttempt,
                flow = webAuthorizationFlowManager.defaultWebAuthorizationFlow,
            )
            return mapRedirectUriToResource(redirectUri)
        }

        val (runResult, runException) = if (onGoingAuthorizeAttempt != null) {
            try {
                run(onGoingAuthorizeAttempt, flow) to null
            } catch (e: BusinessException) {
                null to e
            }
        } else {
            null to null
        }

        if (onGoingAuthorizeAttempt != null && runException != null) {
            authorizeAttemptManager.markAsFailedIfNotRecoverable(
                authorizeAttempt = onGoingAuthorizeAttempt,
                error = runException
            )
        }

        return if (runResult != null && mapResultToResource != null) {
            mapResultToResource(runResult)
        } else {
            val result = webAuthorizationFlowManager.getStatusAndCompleteIfNecessary(
                authorizeAttempt = authorizeAttempt,
            )
            val redirectUri = redirectUriBuilder.getRedirectUri(
                authorizeAttempt = authorizeAttempt,
                flow = flow,
                status = result
            )
            mapRedirectUriToResource(redirectUri)
        }
    }

    /**
     * Call the [run] function with the [OnGoingAuthorizeAttempt], [WebAuthorizationFlow] and [User] associated to the [state].
     * Then run and return the result of:
     * - [mapResultToResource] if the [User] is expected to perform an action to complete the step.
     * - [mapRedirectUriToResource] if the [User] is expected to be redirected to a different step.
     *
     * If the operation is only meant to redirect the end-user to a different step, the [mapResultToResource] parameter may be set to null.
     *
     * It expects the [run] to either return a value if the [User] is expected to perform an action to complete the step
     * or return null if the [User] is expected to be redirected to a different step.
     *
     * [BusinessException] thrown by the [run] function will be caught and handled differently:
     * - if recoverable, the [BusinessException] will be simply thrown to be handled by the exception handler.
     * - if unrecoverable, the [AuthorizeAttempt] will be marked as failed and the end-user will be redirected to the error page.
     */
    suspend fun <Result, FlowResource> fetchOnGoingAttemptWithUserThenRunAndRedirect(
        state: String?,
        run: suspend (OnGoingAuthorizeAttempt, WebAuthorizationFlow, User) -> Result?,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
        mapResultToResource: (suspend (Result) -> FlowResource)? = null,
    ): FlowResource {
        val authorizeAttempt = fetchAuthorizeAttempt(state)
        val flow = try {
            webAuthorizationFlowManager.findById(authorizeAttempt.authorizationFlowId)
        } catch (_: BusinessException) {
            // Redirect to the error page of the default flow since the information on the exact flow is missing.
            val redirectUri = redirectUriBuilder.getErrorUri(
                authorizeAttempt = authorizeAttempt,
                flow = webAuthorizationFlowManager.defaultWebAuthorizationFlow,
            )
            return mapRedirectUriToResource(redirectUri)
        }

        val onGoingAuthorizeAttempt = authorizeAttempt as? OnGoingAuthorizeAttempt
        val user = try {
            userManager.findByIdOrNull(onGoingAuthorizeAttempt?.userId)
        } catch (_: BusinessException) {
            // If the user is missing for the operation, we let the [getStatusAndCompleteIfNecessary] and
            // [getRedirectUri] methods redirect the user to the proper step.
            // ex. the end-user is trying to access the claims validation step before signing in.
            null
        }

        val (runResult, runException) = if (onGoingAuthorizeAttempt != null && user != null) {
            try {
                run(onGoingAuthorizeAttempt, flow, user) to null
            } catch (e: BusinessException) {
                null to e
            }
        } else {
            null to null
        }

        if (onGoingAuthorizeAttempt != null && runException != null) {
            authorizeAttemptManager.markAsFailedIfNotRecoverable(
                authorizeAttempt = onGoingAuthorizeAttempt,
                error = runException
            )
        }

        return if (runResult != null && mapResultToResource != null) {
            mapResultToResource(runResult)
        } else {
            val result = webAuthorizationFlowManager.getStatusAndCompleteIfNecessary(
                authorizeAttempt = authorizeAttempt,
            )
            val redirectUri = redirectUriBuilder.getRedirectUri(
                authorizeAttempt = authorizeAttempt,
                flow = flow,
                status = result
            )
            mapRedirectUriToResource(redirectUri)
        }
    }

    /**
     * Fetches and validates the authorization attempt associated with the given [state].
     *
     * If the state is valid and corresponds to an authorization attempt, the associated
     * [AuthorizeAttempt] is returned. Otherwise, an exception is thrown to indicate an error
     * during the validation process.
     */
    internal suspend fun fetchAuthorizeAttempt(state: String?): AuthorizeAttempt {
        val verifyResult = authorizeAttemptManager.verifyEncodedInternalState(state)
        return when (verifyResult) {
            is SuccessVerifyEncodedStateResult -> verifyResult.authorizeAttempt
            is FailedVerifyEncodedStateResult -> {
                // We cannot redirect the user to a proper error page, we throw to let the error handler still
                // respond with an error but without a redirect uri.
                throw httpExceptionOf(
                    status = HttpStatus.BAD_REQUEST,
                    detailsId = verifyResult.detailsId,
                    descriptionId = verifyResult.descriptionId,
                )
            }
        }
    }
}

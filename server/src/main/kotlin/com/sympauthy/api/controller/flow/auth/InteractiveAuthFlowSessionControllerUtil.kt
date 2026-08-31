package com.sympauthy.api.controller.flow.auth

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.user.User
import io.micronaut.http.HttpStatus
import io.micronaut.security.authentication.Authentication
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

/**
 * Utility class for controller providing APIs for interactive auth flows.
 *
 * It provides utility methods for controller to retrieve the following:
 * - the [OnGoingInteractiveFlowSession] associated to the state in the [Authentication].
 * - the [User] associated to the [OnGoingInteractiveFlowSession].
 * - the [InteractiveFlow] associated to the [OnGoingInteractiveFlowSession].
 */
@Singleton
class InteractiveAuthFlowSessionControllerUtil(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val userManager: UserManager,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper
) {

    /**
     * Resolve the current [com.sympauthy.business.model.flow.InteractiveFlowStep] of the [session] (running
     * any completion transition), then map it to the [URI] where the end-user must be redirected within the
     * [flow].
     */
    private suspend fun redirectToCurrentStep(
        session: InteractiveFlowSession,
        flow: InteractiveFlow
    ): URI {
        val (steppedSession, step) = engine.advance(session)
        return stepUriMapper.toRedirectUri(steppedSession, flow, step)
    }

    /**
     * Call the [run] function with the [OnGoingInteractiveFlowSession] and [InteractiveFlow] associated to the [state].
     * Then run and return the result of the [run] function.
     */
    suspend fun <Resource> fetchOnGoingSessionThenRun(
        state: String?,
        run: suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> Resource
    ): Resource {
        val session = fetchSession(state)
        val flow = interactiveAuthFlowSessionManager.findById(session.flowId)
        val onGoingSession = (session as? OnGoingInteractiveFlowSession) ?: throw httpExceptionOf(
            status = HttpStatus.BAD_REQUEST,
            detailsId = "ctrl.flow.not_ongoing",
        )
        return run(onGoingSession, flow)
    }

    /**
     * Call the [run] function with the [OnGoingInteractiveFlowSession] and [InteractiveFlow] associated to the [state].
     * Then run and return the result of the [run] function.
     */
    suspend fun <Resource> fetchOnGoingSessionWithUserThenRun(
        state: String?,
        run: suspend (OnGoingInteractiveFlowSession, InteractiveFlow, User) -> Resource
    ): Resource {
        return fetchOnGoingSessionThenRun(state) { onGoingSession, flow ->
            val user = userManager.findById(onGoingSession.userId)
            run(onGoingSession, flow, user)
        }
    }

    /**
     * Call the [run] function with the [OnGoingInteractiveFlowSession] and [InteractiveFlow] associated to the [state].
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
     * - if unrecoverable, the [InteractiveFlowSession] will be marked as failed and the end-user will be redirected to the error page.
     */
    suspend fun <Result, FlowResource> fetchOnGoingSessionThenRunAndRedirect(
        state: String?,
        run: suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> Result?,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
        mapResultToResource: (suspend (Result) -> FlowResource)? = null
    ): FlowResource {
        val session = fetchSession(state)
        val onGoingSession = session as? OnGoingInteractiveFlowSession

        val flow = try {
            interactiveAuthFlowSessionManager.findById(session.flowId)
        } catch (_: BusinessException) {
            // Redirect to the error page of the default flow since the information on the exact flow is missing.
            val redirectUri = stepUriMapper.getErrorUri(
                session = session,
                flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow(),
            )
            return mapRedirectUriToResource(redirectUri)
        }

        val (runResult, runException) = if (onGoingSession != null) {
            try {
                run(onGoingSession, flow) to null
            } catch (e: BusinessException) {
                null to e
            }
        } else {
            null to null
        }

        val afterExceptionHandlingSession = handleException(
            session = session,
            exception = runException
        )

        return if (runResult != null && mapResultToResource != null) {
            mapResultToResource(runResult)
        } else {
            val redirectUri = redirectToCurrentStep(afterExceptionHandlingSession, flow)
            mapRedirectUriToResource(redirectUri)
        }
    }

    /**
     * Call the [run] function with the [OnGoingInteractiveFlowSession], [InteractiveFlow] and [User] associated to the [state].
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
     * - if unrecoverable, the [InteractiveFlowSession] will be marked as failed and the end-user will be redirected to the error page.
     */
    suspend fun <Result, FlowResource> fetchOnGoingSessionWithUserThenRunAndRedirect(
        state: String?,
        run: suspend (OnGoingInteractiveFlowSession, InteractiveFlow, User) -> Result?,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
        mapResultToResource: (suspend (Result) -> FlowResource)? = null,
    ): FlowResource {
        val session = fetchSession(state)
        val flow = try {
            interactiveAuthFlowSessionManager.findById(session.flowId)
        } catch (_: BusinessException) {
            // Redirect to the error page of the default flow since the information on the exact flow is missing.
            val redirectUri = stepUriMapper.getErrorUri(
                session = session,
                flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow(),
            )
            return mapRedirectUriToResource(redirectUri)
        }

        val onGoingSession = session as? OnGoingInteractiveFlowSession
        val user = try {
            userManager.findByIdOrNull(onGoingSession?.userId)
        } catch (_: BusinessException) {
            // If the user is missing for the operation, we let the step engine redirect the user to the
            // proper step.
            // ex. the end-user is trying to access the claims validation step before signing in.
            null
        }

        val (runResult, runException) = if (onGoingSession != null && user != null) {
            try {
                run(onGoingSession, flow, user) to null
            } catch (e: BusinessException) {
                null to e
            }
        } else {
            null to null
        }

        val afterExceptionHandlingSession = handleException(
            session = session,
            exception = runException
        )

        return if (runResult != null && mapResultToResource != null) {
            mapResultToResource(runResult)
        } else {
            val redirectUri = redirectToCurrentStep(afterExceptionHandlingSession, flow)
            mapRedirectUriToResource(redirectUri)
        }
    }

    /**
     * Call the [update] function with the [OnGoingInteractiveFlowSession] and [InteractiveFlow] associated to the [state].
     * Return the result of [mapRedirectUriToResource] containing the URI where the end-user must be redirected to
     * continue the authorization flow.
     *
     * [BusinessException] thrown by the [update] function will be caught and handled differently:
     * - if recoverable, the [BusinessException] will be presented as BAD_REQUEST with
     * - if unrecoverable, the [InteractiveFlowSession] will be marked as failed and the end-user will be redirected to the error page.
     *
     * This method is intended to be used by POST operation handling information provided by the end-user to complete
     * a step of the authorization flow. (ex. providing its login and password to sign in).
     */
    suspend fun <FlowResource> fetchOnGoingSessionThenUpdateAndRedirect(
        state: String?,
        update: suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> InteractiveFlowSession,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
    ): FlowResource {
        val session = fetchSession(state)

        val flow = try {
            interactiveAuthFlowSessionManager.findById(session.flowId)
        } catch (_: BusinessException) {
            // Redirect to the error page of the default flow since the information on the exact flow is missing.
            val redirectUri = stepUriMapper.getErrorUri(
                session = session,
                flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow(),
            )
            return mapRedirectUriToResource(redirectUri)
        }

        var afterUpdateSession = session
        val onGoingSession = session as? OnGoingInteractiveFlowSession

        val updateException = if (onGoingSession != null) {
            try {
                afterUpdateSession = update(onGoingSession, flow)
                null
            } catch (e: BusinessException) {
                e
            }
        } else null

        val afterExceptionHandlingSession = handleException(
            session = afterUpdateSession,
            exception = updateException
        )

        val redirectUri = redirectToCurrentStep(afterExceptionHandlingSession, flow)
        return mapRedirectUriToResource(redirectUri)
    }

    /**
     * Call the [update] function with the [OnGoingInteractiveFlowSession], [InteractiveFlow] and [User] associated to the [state].
     * Return the result of [mapRedirectUriToResource] containing the URI where the end-user must be redirected to
     * continue the authorization flow.
     *
     * [BusinessException] thrown by the [update] function will be caught and handled differently:
     * - if recoverable, the [BusinessException] will be presented as BAD_REQUEST with
     * - if unrecoverable, the [InteractiveFlowSession] will be marked as failed and the end-user will be redirected to the error page.
     *
     * This method is intended to be used by POST operation handling information provided by the end-user to complete
     * a step of the authorization flow. (ex. providing its login and password to sign in).
     */
    suspend fun <FlowResource> fetchOnGoingSessionWithUserThenUpdateAndRedirect(
        state: String?,
        update: suspend (OnGoingInteractiveFlowSession, InteractiveFlow, User) -> InteractiveFlowSession,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
    ): FlowResource {
        val session = fetchSession(state)

        val flow = try {
            interactiveAuthFlowSessionManager.findById(session.flowId)
        } catch (_: BusinessException) {
            // Redirect to the error page of the default flow since the information on the exact flow is missing.
            val redirectUri = stepUriMapper.getErrorUri(
                session = session,
                flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow(),
            )
            return mapRedirectUriToResource(redirectUri)
        }

        var afterUpdateSession = session
        val onGoingSession = session as? OnGoingInteractiveFlowSession
        val user = try {
            userManager.findByIdOrNull(onGoingSession?.userId)
        } catch (_: BusinessException) {
            // If the user is missing for the operation, we let the step engine redirect the user to the
            // proper step.
            // ex. the end-user is trying to access the claims validation step before signing in.
            null
        }

        val updateException = if (onGoingSession != null && user != null) {
            try {
                afterUpdateSession = update(onGoingSession, flow, user)
                null
            } catch (e: BusinessException) {
                e
            }
        } else null

        val afterExceptionHandlingSession = handleException(
            session = afterUpdateSession,
            exception = updateException
        )

        val redirectUri = redirectToCurrentStep(afterExceptionHandlingSession, flow)
        return mapRedirectUriToResource(redirectUri)
    }

    /**
     * Fetches and validates the interactive flow session associated with the given [state].
     *
     * If the state is valid and corresponds to a session, the associated [InteractiveFlowSession] is
     * returned. Otherwise, an exception is thrown to indicate an error during the validation process.
     */
    internal suspend fun fetchSession(state: String?): InteractiveFlowSession {
        val verifyResult = sessionManager.verifyEncodedInternalState(state)
        return when (verifyResult) {
            is SuccessVerifyEncodedStateResult -> verifyResult.session
            is FailedVerifyEncodedStateResult -> {
                // We cannot redirect the user to a proper error page, we throw to let the error handler still
                // respond with an error but without a redirect uri.
                throw LocalizedHttpException(
                    status = HttpStatus.BAD_REQUEST,
                    detailsId = verifyResult.detailsId,
                    descriptionId = verifyResult.descriptionId,
                    values = verifyResult.values,
                )
            }
        }
    }

    /**
     * Handles a business exception that occurred during the business logic of the authorization flow.
     *
     * If the exception is recoverable, it is thrown to be handled by the caller.
     * If the exception is not recoverable and the session is ongoing, the session is marked as failed
     * with the specific error.
     *
     * The [session] is just returned if the exception is null. Or returns the [session] updated
     * by the [InteractiveFlowSessionManager.markAsFailedIfNotRecoverable] method.
     */
    internal suspend fun handleException(
        session: InteractiveFlowSession,
        exception: BusinessException?
    ): InteractiveFlowSession {
        if (exception == null) {
            return session
        }
        if (exception.recoverable) {
            throw exception
        }
        // A concurrent-modification conflict means another request advanced this shared session. If that
        // request already drove it to a terminal state, route by that current state instead of failing it
        // (which would clobber the winner): e.g. a double-submit whose sibling already completed sends this
        // request to the success redirect rather than the error page. If it is still ongoing we fall
        // through and fail it, honouring the "a conflict fails the session" behaviour.
        if (exception.detailsId == InteractiveFlowSessionManager.CONCURRENT_MODIFICATION_DETAILS_ID) {
            val current = sessionManager.fetchByIdOrNull(session.id)
            if (current != null && current !is OnGoingInteractiveFlowSession) {
                return current
            }
        }
        return if (session is OnGoingInteractiveFlowSession) {
            sessionManager.markAsFailedIfNotRecoverable(
                session = session,
                error = exception
            )
        } else session
    }
}

package com.sympauthy.api.controller.flow.auth

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.VerifyEncodedStateResult
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.security.UserSecurityContextManager
import com.sympauthy.business.model.flow.CONCURRENT_MODIFICATION_DETAILS_ID
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.interactiveFlowSessionAlreadyCompletedExceptionOf
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.business.model.user.User
import com.sympauthy.exception.toOutcome
import com.sympauthy.monitoring.telemetry.CustomSpanTracer
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.measureOutcome
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
 *
 * **It is also where a request against a session is recorded as having been seen.** Every flow handler
 * comes through here to resolve one, and every controller that starts one comes through
 * [observeStartedSession] — which is what makes one place enough: a step added later is observed by having
 * been written the ordinary way. The [ObservedRequest] is threaded in from the controller that bound it
 * rather than reached back for, which `docs/design/security-context.md` refuses.
 */
@Singleton
class InteractiveAuthFlowSessionControllerUtil(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val userSecurityContextManager: UserSecurityContextManager,
    @Inject private val customSpanTracer: CustomSpanTracer
) {

    /**
     * Resolve the current [InteractiveFlowStep] of the [session] (running any completion transition), then map
     * it to the [URI] where the end-user must be redirected within the [flow].
     */
    private suspend fun redirectToCurrentStep(
        session: InteractiveFlowSession,
        flow: InteractiveFlow
    ): URI {
        val (steppedSession, step) = advance(session)
        return stepUriMapper.toRedirectUri(steppedSession, flow, step)
    }

    /**
     * Resolve the current [InteractiveFlowStep] of the [session], running any completion transition.
     *
     * A [BusinessException] moving the flow forward is handled as one thrown by the work before it, because
     * completing the session is the last part of that work: a promotion finding an identifier taken fails the
     * session, and a completion that lost to a concurrent request is routed by the session that request left.
     */
    private suspend fun advance(session: InteractiveFlowSession): InteractiveFlowStepResult {
        return try {
            engine.advance(session)
        } catch (e: BusinessException) {
            engine.advance(handleException(session, e))
        }
    }

    /**
     * Call the [run] function with the [OnGoingInteractiveFlowSession] and [InteractiveFlow] associated to the [state].
     * Then run and return the result of the [run] function.
     */
    suspend fun <Resource> fetchOnGoingSessionThenRun(
        state: String?,
        observedRequest: ObservedRequest,
        run: suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> Resource
    ): Resource {
        val session = fetchSessionAndObserveRequest(state, observedRequest)
        val flow = interactiveAuthFlowSessionManager.findById(session.flowId)
        val onGoingSession = (session as? OnGoingInteractiveFlowSession) ?: throw httpExceptionOf(
            status = HttpStatus.BAD_REQUEST,
            detailsId = "ctrl.flow.not_ongoing",
            descriptionId = "description.ctrl.flow.not_ongoing",
        )
        return run(onGoingSession, flow)
    }

    /**
     * Call the [run] function with the [OnGoingInteractiveFlowSession] and [InteractiveFlow] associated to the [state].
     * Then run and return the result of the [run] function.
     */
    suspend fun <Resource> fetchOnGoingSessionWithUserThenRun(
        state: String?,
        observedRequest: ObservedRequest,
        run: suspend (OnGoingInteractiveFlowSession, InteractiveFlow, User) -> Resource
    ): Resource {
        return fetchOnGoingSessionThenRun(state, observedRequest) { onGoingSession, flow ->
            val user = sessionManager.getUser(onGoingSession)
            run(onGoingSession, flow, user)
        }
    }

    /**
     * Call the [run] function with the [OnGoingInteractiveFlowSession] and [InteractiveFlow] associated to the [state].
     * Then run and return the result of:
     * - [mapRedirectUriToResource] if the end-user is expected to be redirected to a different step.
     * - [mapResultToResource] if the end-user is expected to perform an action to complete the step.
     *
     * If the operation is only meant to redirect the end-user to a different step, the [mapResultToResource] parameter
     * may be set to null.
     *
     * It expects the [run] to either return a value if the [User] is expected to perform an action to complete the step
     * or return null if the [User] is expected to be redirected to a different step.
     *
     * [BusinessException] thrown by the [run] function will be caught and handled differently:
     * - if recoverable, the [BusinessException] will be simply thrown to be handled by the exception handler.
     * - if unrecoverable, the [InteractiveFlowSession] will be marked as failed and the end-user will be redirected to
     *   the error page.
     */
    suspend fun <Result, FlowResource> fetchOnGoingSessionThenRunAndRedirect(
        state: String?,
        observedRequest: ObservedRequest,
        run: suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> Result?,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
        mapResultToResource: (suspend (Result) -> FlowResource)? = null
    ): FlowResource {
        val session = fetchSessionAndObserveRequest(state, observedRequest)
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
     * Call the [run] function with the [OnGoingInteractiveFlowSession], [InteractiveFlow] and [User] associated to the
     * [state]. Then run and return the result of:
     * - [mapResultToResource] if the [User] is expected to perform an action to complete the step.
     * - [mapRedirectUriToResource] if the [User] is expected to be redirected to a different step.
     *
     * If the operation is only meant to redirect the end-user to a different step, the [mapResultToResource] parameter
     * may be set to null.
     *
     * It expects the [run] to either return a value if the [User] is expected to perform an action to complete the step
     * or return null if the [User] is expected to be redirected to a different step.
     *
     * [BusinessException] thrown by the [run] function will be caught and handled differently:
     * - if recoverable, the [BusinessException] will be simply thrown to be handled by the exception handler.
     * - if unrecoverable, the [InteractiveFlowSession] will be marked as failed and the end-user will be redirected to
     *   the error page.
     */
    suspend fun <Result, FlowResource> fetchOnGoingSessionWithUserThenRunAndRedirect(
        state: String?,
        observedRequest: ObservedRequest,
        run: suspend (OnGoingInteractiveFlowSession, InteractiveFlow, User) -> Result?,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
        mapResultToResource: (suspend (Result) -> FlowResource)? = null,
    ): FlowResource {
        val session = fetchSessionAndObserveRequest(state, observedRequest)
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
            onGoingSession?.let { sessionManager.getUserOrNull(it) }
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
     * Call the [update] function with the [OnGoingInteractiveFlowSession] and [InteractiveFlow] associated to the
     * [state]. Return the result of [mapRedirectUriToResource] containing the URI where the end-user must be redirected
     * to continue the authorization flow.
     *
     * [BusinessException] thrown by the [update] function will be caught and handled differently:
     * - if recoverable, the [BusinessException] will be presented as BAD_REQUEST with
     * - if unrecoverable, the [InteractiveFlowSession] will be marked as failed and the end-user will be redirected to
     *   the error page.
     *
     * [update] applies what the end-user submitted to complete the current step of the authorization flow
     * (ex. the login and password they signed in with), and answers the session that resulted.
     */
    suspend fun <FlowResource> fetchOnGoingSessionThenUpdateAndRedirect(
        state: String?,
        observedRequest: ObservedRequest,
        update: suspend (OnGoingInteractiveFlowSession, InteractiveFlow) -> InteractiveFlowSession,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
    ): FlowResource {
        val session = fetchSessionAndObserveRequest(state, observedRequest)

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
     * Call the [update] function with the [OnGoingInteractiveFlowSession], [InteractiveFlow] and [User] associated to
     * the [state]. Return the result of [mapRedirectUriToResource] containing the URI where the end-user must be
     * redirected to continue the authorization flow.
     *
     * [BusinessException] thrown by the [update] function will be caught and handled differently:
     * - if recoverable, the [BusinessException] will be presented as BAD_REQUEST with
     * - if unrecoverable, the [InteractiveFlowSession] will be marked as failed and the end-user will be redirected to
     *   the error page.
     *
     * [update] applies what the end-user submitted to complete the current step of the authorization flow
     * (ex. the login and password they signed in with), and answers the session that resulted.
     */
    suspend fun <FlowResource> fetchOnGoingSessionWithUserThenUpdateAndRedirect(
        state: String?,
        observedRequest: ObservedRequest,
        update: suspend (OnGoingInteractiveFlowSession, InteractiveFlow, User) -> InteractiveFlowSession,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
    ): FlowResource {
        val session = fetchSessionAndObserveRequest(state, observedRequest)

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
            onGoingSession?.let { sessionManager.getUserOrNull(it) }
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
     * Resolve the session associated to the [state] for the error page, and return the result of:
     * - [mapFailureToResource] with the failure it ended on, where it is over and its step leads to the error
     *   page.
     * - [mapRedirectUriToResource] with where the end-user must be redirected instead, where it does not.
     *
     * **The error page is the one handler that answers a failure rather than redirecting to it**, so this is
     * where it differs from the other entry points, and only there: a [state] that verifies to no session is
     * answered rather than thrown, and a step leading to the error page is answered with its failure rather
     * than a redirect back to the page asking. A session that has not failed is otherwise resolved as anywhere
     * else — where the request came from is recorded, and a failure moving it forward fails it.
     *
     * So is a flow no longer configured. The other entry points send its session to the default flow's error
     * page, which is this one: here it fails the session, so the failure is the session's to say from then on.
     */
    suspend fun <FlowResource> fetchSessionThenAnswerFailureOrRedirect(
        state: String?,
        observedRequest: ObservedRequest,
        mapFailureToResource: suspend (BusinessException) -> FlowResource,
        mapRedirectUriToResource: suspend (URI) -> FlowResource,
    ): FlowResource {
        val session = when (val verifyResult = verifyStateAndObserveRequest(state, observedRequest)) {
            is SuccessVerifyEncodedStateResult -> verifyResult.session
            is FailedVerifyEncodedStateResult -> return mapFailureToResource(
                failureOf(verifyResult.detailsId, verifyResult.descriptionId, verifyResult.values)
            )
        }

        // An ongoing session's flow is resolved before the engine is asked, since asking may complete it. One
        // that is over is told so without it, so a flow no longer configured stops only its redirect.
        var resolvedSession = session
        val flow = if (session is OnGoingInteractiveFlowSession) {
            try {
                interactiveAuthFlowSessionManager.findById(session.flowId)
            } catch (e: BusinessException) {
                resolvedSession = handleException(session, e)
                null
            }
        } else null

        val (steppedSession, step) = advance(resolvedSession)
        return when {
            step == InteractiveFlowStep.AlreadyCompleted ->
                mapFailureToResource(interactiveFlowSessionAlreadyCompletedExceptionOf())

            steppedSession is FailedInteractiveFlowSession -> mapFailureToResource(
                failureOf(
                    detailsId = steppedSession.errorDetailsId,
                    descriptionId = steppedSession.errorDescriptionId,
                    values = steppedSession.errorValues.orEmpty()
                )
            )

            else -> {
                val redirectFlow = flow ?: interactiveAuthFlowSessionManager.findById(steppedSession.flowId)
                mapRedirectUriToResource(stepUriMapper.toRedirectUri(steppedSession, redirectFlow, step))
            }
        }
    }

    /**
     * The non-recoverable failure named by [detailsId] and [descriptionId], carrying the [values] both of its
     * messages interpolate.
     *
     * Stated once rather than at every branch that reaches it: a failure carries the values its messages
     * name, and a branch rebuilding the exception on its own is a branch that can be written without them.
     */
    private fun failureOf(
        detailsId: String,
        descriptionId: String?,
        values: Map<String, String>
    ) = BusinessException(
        recoverable = false,
        detailsId = detailsId,
        descriptionId = descriptionId,
        values = values
    )

    /**
     * Record that the just-created [session] was started from [observedRequest].
     *
     * **For the controller that created it, rather than for the manager that wrote it.** A session is
     * written in a transaction of its own, and recording must never fail a flow: a statement that failed
     * inside that transaction would abort it on PostgreSQL and take the creation down with it. Calling it
     * here also keeps the [ObservedRequest] out of the managers, which have no other use for one.
     *
     * Call it before the engine is asked what comes next, so a session that resolves immediately is folded
     * with the place it was started from rather than after it.
     */
    suspend fun observeStartedSession(session: InteractiveFlowSession, observedRequest: ObservedRequest) {
        userSecurityContextManager.observe(session.id, observedRequest)
    }

    /**
     * Record [session] as driven from [observedRequest], where it is still a session a request can drive.
     *
     * **A session that has reached a terminal state is not recorded against.** Completing one folds the
     * place a credential was proven at into that person's record and consumes every row it held, and the
     * signed state outlives the flow — so a replay would re-open rows on a session that is over, which
     * only the cleaner would then collect.
     *
     * **An expired one is not either, and that is the sharper reason.** `InteractiveFlowSessionCleaner`
     * deletes a session's places and the session itself in one transaction: a row inserted between the
     * two takes a key lock the parent delete then fails on, and on PostgreSQL that rolls the whole
     * collection back — every expired session and validation code with it, until a run gets through.
     *
     * A session created already failed is still recorded, by the entry point that created it: that row is
     * the only thing such a session will ever say about the browser that started it.
     */
    private suspend fun observeIfStillDrivable(
        session: InteractiveFlowSession,
        observedRequest: ObservedRequest
    ) {
        if (session !is OnGoingInteractiveFlowSession || session.expired) {
            return
        }
        userSecurityContextManager.observe(session.id, observedRequest)
    }

    /**
     * Fetches and validates the interactive flow session associated with the given [state], and records
     * that it was driven from [observedRequest].
     *
     * If the state is valid and corresponds to a session, the associated [InteractiveFlowSession] is
     * returned. Otherwise, an exception is thrown to indicate an error during the validation process.
     *
     * The recording happens here rather than at each entry point above, because it is the one thing every
     * one of them does — and after the state has verified, so a request naming no session writes nothing.
     */
    internal suspend fun fetchSessionAndObserveRequest(
        state: String?,
        observedRequest: ObservedRequest
    ): InteractiveFlowSession {
        return when (val verifyResult = verifyStateAndObserveRequest(state, observedRequest)) {
            is SuccessVerifyEncodedStateResult -> verifyResult.session
            is FailedVerifyEncodedStateResult -> {
                // We cannot redirect the user to a proper error page, we throw to let the error handler still
                // respond with an error but without a redirect uri.
                throw LocalizedHttpException(
                    status = HttpStatus.BAD_REQUEST,
                    recoverable = false,
                    detailsId = verifyResult.detailsId,
                    descriptionId = verifyResult.descriptionId,
                    values = verifyResult.values,
                )
            }
        }
    }

    /**
     * Verify the [state], and record that the session it names was driven from [observedRequest] — after it
     * has verified, so a request naming no session writes nothing.
     *
     * Traced as the check of the state, so that the session's read and the record's write read apart from the
     * route's own work. A state refused is `refused`, typed by the code it was refused with.
     */
    private suspend fun verifyStateAndObserveRequest(
        state: String?,
        observedRequest: ObservedRequest
    ): VerifyEncodedStateResult = customSpanTracer.validateState { span ->
        measureOutcome(
            onSuccess = { _, result ->
                span.setOutcome(
                    when (result) {
                        is SuccessVerifyEncodedStateResult -> Outcome.Success
                        is FailedVerifyEncodedStateResult -> Outcome.Refused(result.detailsId)
                    }
                )
            },
            onFailure = { _, failure -> span.setOutcome(failure.toOutcome()) }
        ) {
            sessionManager.verifyEncodedInternalState(state).also {
                if (it is SuccessVerifyEncodedStateResult) {
                    observeIfStillDrivable(it.session, observedRequest)
                }
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
        // (which would clobber the winner): a double-submit whose sibling already completed it is answered
        // as any later request on a completed session is. If it is still ongoing we fall through and fail
        // it, honouring the "a conflict fails the session" behaviour.
        if (exception.detailsId == CONCURRENT_MODIFICATION_DETAILS_ID) {
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

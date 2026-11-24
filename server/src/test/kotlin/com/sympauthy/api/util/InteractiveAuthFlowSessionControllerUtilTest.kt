package com.sympauthy.api.util

import com.sympauthy.api.errorhandler.LocalizedExceptionHandler
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.InteractiveFlowStepUriMapper
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.flow.auth.manager.InteractiveAuthFlowSessionManager
import com.sympauthy.business.flow.manager.FailedVerifyEncodedStateResult
import com.sympauthy.business.flow.manager.InteractiveFlowEngine
import com.sympauthy.business.flow.manager.InteractiveFlowSessionManager
import com.sympauthy.business.flow.manager.SuccessVerifyEncodedStateResult
import com.sympauthy.business.flow.model.CONCURRENT_MODIFICATION_DETAILS_ID
import com.sympauthy.business.flow.model.CancelledInteractiveFlowSession
import com.sympauthy.business.flow.model.CompletedInteractiveFlowSession
import com.sympauthy.business.flow.model.FailedInteractiveFlowSession
import com.sympauthy.business.flow.model.InteractiveFlow
import com.sympauthy.business.flow.model.InteractiveFlowHandBack
import com.sympauthy.business.flow.model.InteractiveFlowPurpose
import com.sympauthy.business.flow.model.InteractiveFlowRedirectType
import com.sympauthy.business.flow.model.InteractiveFlowSession
import com.sympauthy.business.flow.model.InteractiveFlowStep
import com.sympauthy.business.flow.model.InteractiveFlowStepResult
import com.sympauthy.business.flow.model.OnGoingInteractiveFlowSession
import com.sympauthy.business.securitycontext.edge.model.observedRequestOf
import com.sympauthy.business.securitycontext.manager.UserSecurityContextManager
import com.sympauthy.business.user.manager.UserManager
import com.sympauthy.platform.exception.platformExceptionOf
import com.sympauthy.platform.monitoring.telemetry.manager.CustomSpanTracer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.net.URI
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveAuthFlowSessionControllerUtilTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    @MockK(relaxed = true)
    lateinit var userSecurityContextManager: UserSecurityContextManager

    @SpyK
    var customSpanTracer = CustomSpanTracer(OpenTelemetry.noop())

    @MockK(relaxUnitFun = true)
    lateinit var exceptionHandler: LocalizedExceptionHandler

    @InjectMockKs
    lateinit var util: InteractiveAuthFlowSessionControllerUtil

    private val concurrentModification = CONCURRENT_MODIFICATION_DETAILS_ID

    private fun ongoing(sessionId: UUID) = mockk<OnGoingInteractiveFlowSession> {
        every { id } returns sessionId
        every { expired } returns false
    }

    /** Expired but still ongoing: the shape the cleaner collects, and the one the guard turns on. */
    private fun expiredOngoing() = mockk<OnGoingInteractiveFlowSession> {
        every { expired } returns true
    }

    private val observed = observedRequestOf()

    private val plainHandBack = InteractiveFlowHandBack(
        uri = URI.create("https://client.example.com/account"),
        type = InteractiveFlowRedirectType.PLAIN
    )

    @Test
    fun `fetchSessionAndObserveRequest - Records where the request that resolved the session came from`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = ongoing(sessionId)
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)

        assertSame(session, util.fetchSessionAndObserveRequest("encoded-state", observed))

        coVerify { userSecurityContextManager.observe(sessionId, observed) }
    }

    /**
     * Completing a flow folds its proven place into the person's record and consumes every row it held,
     * and the state outlives the flow — a replay must not re-open one.
     */
    @Test
    fun `fetchSessionAndObserveRequest - Records nothing against a session that has reached its end`() =
        runTest {
            val session = mockk<CompletedInteractiveFlowSession>()
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                SuccessVerifyEncodedStateResult(session)

            assertSame(session, util.fetchSessionAndObserveRequest("encoded-state", observed))

            coVerify(exactly = 0) { userSecurityContextManager.observe(any(), any()) }
        }

    /** The cleaner deletes an expired session and its places in one transaction; a row landing between
     * the two fails the delete's foreign key and rolls the whole collection back. */
    @Test
    fun `fetchSessionAndObserveRequest - Records nothing against a session the cleaner is entitled to`() =
        runTest {
            val session = expiredOngoing()
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                SuccessVerifyEncodedStateResult(session)

            util.fetchSessionAndObserveRequest("encoded-state", observed)

            coVerify(exactly = 0) { userSecurityContextManager.observe(any(), any()) }
        }

    @Test
    fun `fetchSessionAndObserveRequest - Records nothing where the state named no session`() = runTest {
        coEvery { sessionManager.verifyEncodedInternalState("forged-state") } returns
            FailedVerifyEncodedStateResult("flow.state.invalid")

        assertThrows<LocalizedHttpException> { util.fetchSessionAndObserveRequest("forged-state", observed) }

        coVerify(exactly = 0) { userSecurityContextManager.observe(any(), any()) }
    }

    @Test
    fun `observeStartedSession - Records where the session a controller just created was started from`() =
        runTest {
            val sessionId = UUID.randomUUID()
            val session = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }

            util.observeStartedSession(session, observed)

            coVerify { userSecurityContextManager.observe(sessionId, observed) }
        }

    /** A promotion finding an identifier taken refuses the completion, which the step's work left to here. */
    @Test
    fun `fetchOnGoingSessionThenUpdateAndRedirect - Fails the session where completing it is refused`() =
        runTest {
            val errorUri = URI.create("https://flow.example.com/error?state=encoded-state")
            val session = ongoingInFlow(UUID.randomUUID())
            val updated = mockk<OnGoingInteractiveFlowSession>()
            val failed = mockk<FailedInteractiveFlowSession>()
            val flow = mockk<InteractiveFlow>()
            val refusal = businessExceptionOf("user.promote.identifier_taken")
            stubFlowOf(session, flow)
            coEvery { engine.advance(updated) } throws refusal
            coEvery { sessionManager.markAsFailedIfNotRecoverable(updated, refusal) } returns failed
            coEvery { engine.advance(failed) } returns InteractiveFlowStepResult(failed, InteractiveFlowStep.Error)
            coEvery { stepUriMapper.toRedirectUri(failed, flow, InteractiveFlowStep.Error) } returns errorUri

            val result = util.fetchOnGoingSessionThenUpdateAndRedirect(
                state = "encoded-state",
                observedRequest = observed,
                update = { _, _ -> updated },
                mapRedirectUriToResource = { it }
            )

            assertEquals(errorUri, result)
        }

    @Test
    fun `fetchOnGoingSessionThenUpdateAndRedirect - Fails the session where a mechanism it rests on fails`() =
        runTest {
            val errorUri = URI.create("https://flow.example.com/error?state=encoded-state")
            val session = ongoingInFlow(UUID.randomUUID())
            val failed = mockk<FailedInteractiveFlowSession>()
            val flow = mockk<InteractiveFlow>()
            val failure = platformExceptionOf("lock.second")
            stubFlowOf(session, flow)
            coEvery { sessionManager.markAsFailedIfNotRecoverable(session, failure) } returns failed
            coEvery { engine.advance(failed) } returns InteractiveFlowStepResult(failed, InteractiveFlowStep.Error)
            coEvery { stepUriMapper.toRedirectUri(failed, flow, InteractiveFlowStep.Error) } returns errorUri

            val result = util.fetchOnGoingSessionThenUpdateAndRedirect(
                state = "encoded-state",
                observedRequest = observed,
                update = { _, _ -> throw failure },
                mapRedirectUriToResource = { it }
            )

            assertEquals(errorUri, result)
        }

    @Test
    fun `fetchOnGoingSessionWithUserThenUpdateAndRedirect - Fails the session where its user cannot be read`() =
        runTest {
            val errorUri = URI.create("https://flow.example.com/error?state=encoded-state")
            val session = ongoingInFlow(UUID.randomUUID())
            val failed = mockk<FailedInteractiveFlowSession>()
            val flow = mockk<InteractiveFlow>()
            val failure = internalBusinessExceptionOf(
                "mapper.user.invalid_property",
                IllegalArgumentException("No enum constant UserStatus.FOO"),
                "property" to "status"
            )
            stubFlowOf(session, flow)
            coEvery { sessionManager.getUserOrNull(session) } throws failure
            coEvery { sessionManager.markAsFailedIfNotRecoverable(session, failure) } returns failed
            coEvery { engine.advance(failed) } returns InteractiveFlowStepResult(failed, InteractiveFlowStep.Error)
            coEvery { stepUriMapper.toRedirectUri(failed, flow, InteractiveFlowStep.Error) } returns errorUri

            val result = util.fetchOnGoingSessionWithUserThenUpdateAndRedirect(
                state = "encoded-state",
                observedRequest = observed,
                update = { _, _, _ -> fail("Updated a session whose user could not be read") },
                mapRedirectUriToResource = { it }
            )

            assertEquals(errorUri, result)
            verify { exceptionHandler.record(failure, failure) }
        }

    @Test
    fun `fetchOnGoingSessionWithUserThenUpdateAndRedirect - Redirects a session with no user yet, updating nothing`() =
        runTest {
            val stepUri = URI.create("https://flow.example.com/sign-in?state=encoded-state")
            val session = ongoingInFlow(UUID.randomUUID())
            val flow = mockk<InteractiveFlow>()
            stubFlowOf(session, flow)
            coEvery { sessionManager.getUserOrNull(session) } returns null
            coEvery { engine.advance(session) } returns InteractiveFlowStepResult(session, InteractiveFlowStep.SignIn)
            coEvery { stepUriMapper.toRedirectUri(session, flow, InteractiveFlowStep.SignIn) } returns stepUri

            val result = util.fetchOnGoingSessionWithUserThenUpdateAndRedirect(
                state = "encoded-state",
                observedRequest = observed,
                update = { _, _, _ -> fail("Updated a session with no user") },
                mapRedirectUriToResource = { it }
            )

            assertEquals(stepUri, result)
        }

    @Test
    fun `fetchOnGoingSessionWithUserThenRunAndRedirect - Fails the session where its user cannot be read`() =
        runTest {
            val errorUri = URI.create("https://flow.example.com/error?state=encoded-state")
            val session = ongoingInFlow(UUID.randomUUID())
            val failed = mockk<FailedInteractiveFlowSession>()
            val flow = mockk<InteractiveFlow>()
            val failure = internalBusinessExceptionOf(
                "mapper.user.invalid_property",
                IllegalArgumentException("No enum constant UserStatus.FOO"),
                "property" to "status"
            )
            stubFlowOf(session, flow)
            coEvery { sessionManager.getUserOrNull(session) } throws failure
            coEvery { sessionManager.markAsFailedIfNotRecoverable(session, failure) } returns failed
            coEvery { engine.advance(failed) } returns InteractiveFlowStepResult(failed, InteractiveFlowStep.Error)
            coEvery { stepUriMapper.toRedirectUri(failed, flow, InteractiveFlowStep.Error) } returns errorUri

            val result = util.fetchOnGoingSessionWithUserThenRunAndRedirect<Unit, URI>(
                state = "encoded-state",
                observedRequest = observed,
                run = { _, _, _ -> fail("Ran a step whose user could not be read") },
                mapRedirectUriToResource = { it }
            )

            assertEquals(errorUri, result)
            verify { exceptionHandler.record(failure, failure) }
        }

    @Test
    fun `fetchOnGoingSessionWithUserThenRunAndRedirect - Redirects a session with no user yet, running nothing`() =
        runTest {
            val stepUri = URI.create("https://flow.example.com/sign-in?state=encoded-state")
            val session = ongoingInFlow(UUID.randomUUID())
            val flow = mockk<InteractiveFlow>()
            stubFlowOf(session, flow)
            coEvery { sessionManager.getUserOrNull(session) } returns null
            coEvery { engine.advance(session) } returns InteractiveFlowStepResult(session, InteractiveFlowStep.SignIn)
            coEvery { stepUriMapper.toRedirectUri(session, flow, InteractiveFlowStep.SignIn) } returns stepUri

            val result = util.fetchOnGoingSessionWithUserThenRunAndRedirect<Unit, URI>(
                state = "encoded-state",
                observedRequest = observed,
                run = { _, _, _ -> fail("Ran a step with no user") },
                mapRedirectUriToResource = { it }
            )

            assertEquals(stepUri, result)
        }

    @Test
    fun `fetchOnGoingSessionThenUpdateAndRedirect - Routes a lost completion by where the winner left it`() =
        runTest {
            val errorUri = URI.create("https://flow.example.com/error?state=encoded-state")
            val session = ongoingInFlow(UUID.randomUUID())
            val updated = mockk<OnGoingInteractiveFlowSession> { every { id } returns UUID.randomUUID() }
            val completed = mockk<CompletedInteractiveFlowSession>()
            val flow = mockk<InteractiveFlow>()
            stubFlowOf(session, flow)
            coEvery { engine.advance(updated) } throws businessExceptionOf(concurrentModification)
            coEvery { sessionManager.fetchByIdOrNull(updated.id) } returns completed
            coEvery { engine.advance(completed) } returns
                InteractiveFlowStepResult(completed, InteractiveFlowStep.AlreadyCompleted)
            coEvery {
                stepUriMapper.toRedirectUri(completed, flow, InteractiveFlowStep.AlreadyCompleted)
            } returns errorUri

            val result = util.fetchOnGoingSessionThenUpdateAndRedirect(
                state = "encoded-state",
                observedRequest = observed,
                update = { _, _ -> updated },
                mapRedirectUriToResource = { it }
            )

            assertEquals(errorUri, result)
            coVerify(exactly = 0) { sessionManager.markAsFailedIfNotRecoverable(any(), any()) }
        }

    @Test
    fun `fetchSessionThenAnswerFailureOrRedirect - Answers a failed session with the values of its failure`() =
        runTest {
            val session = failedSession(errorValues = mapOf("expirationDate" to "2026-08-31T10:00"))
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                SuccessVerifyEncodedStateResult(session)
            coEvery { engine.advance(session) } returns InteractiveFlowStepResult(session, InteractiveFlowStep.Error)

            val (result, answeredSession) = answerErrorPageWithSession()

            assertEquals("auth.interactive_flow_session.validate.expired", result.detailsId)
            assertEquals("description.oauth2.expired", result.descriptionId)
            assertEquals(mapOf("expirationDate" to "2026-08-31T10:00"), result.values)
            assertSame(session, answeredSession)
        }

    @Test
    fun `fetchSessionThenAnswerFailureOrRedirect - Answers a state that verifies to no session with its refusal`() =
        runTest {
            val sessionId = UUID.randomUUID().toString()
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                FailedVerifyEncodedStateResult(
                    detailsId = "auth.interactive_flow_session.validate.missing_session",
                    descriptionId = "description.oauth2.expired",
                    values = mapOf("sessionId" to sessionId)
                )

            val (result, answeredSession) = answerErrorPageWithSession()

            assertEquals("auth.interactive_flow_session.validate.missing_session", result.detailsId)
            assertEquals(mapOf("sessionId" to sessionId), result.values)
            assertNull(answeredSession)
            coVerify(exactly = 0) { userSecurityContextManager.observe(any(), any()) }
        }

    /** The step leads to the error page, so a redirect would send the browser back to the page asking. */
    @Test
    fun `fetchSessionThenAnswerFailureOrRedirect - Answers a session already handed off with that failure`() =
        runTest {
            val session = mockk<CompletedInteractiveFlowSession>()
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                SuccessVerifyEncodedStateResult(session)
            coEvery { engine.advance(session) } returns
                InteractiveFlowStepResult(session, InteractiveFlowStep.AlreadyCompleted)

            val result = answerErrorPage()

            assertEquals("auth.interactive_flow_session.already_completed", result.detailsId)
            assertEquals("description.auth.interactive_flow_session.already_completed", result.descriptionId)
            coVerify(exactly = 0) { stepUriMapper.toRedirectUri(any(), any(), any()) }
            // Telling a session it is over needs no flow, so one no longer configured does not stop it.
            verify(exactly = 0) { interactiveAuthFlowSessionManager.findById(any()) }
        }

    @Test
    fun `fetchSessionThenAnswerFailureOrRedirect - Records and redirects an ongoing session`() =
        runTest {
            val redirectUri = URI.create("https://flow.example.com/sign-in?state=encoded-state")
            val sessionId = UUID.randomUUID()
            val session = ongoingInFlow(sessionId)
            val steppedSession = mockk<OnGoingInteractiveFlowSession>()
            val flow = mockk<InteractiveFlow>()
            stubFlowOf(session, flow)
            coEvery { engine.advance(session) } returns
                InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.SignIn)
            coEvery { stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.SignIn) } returns
                redirectUri

            val result = util.fetchSessionThenAnswerFailureOrRedirect(
                state = "encoded-state",
                observedRequest = observed,
                mapFailureToResource = { failure, _ -> fail("Answered a failure: ${failure.detailsId}") },
                mapRedirectUriToResource = { it }
            )

            assertEquals(redirectUri, result)
            coVerify { userSecurityContextManager.observe(sessionId, observed) }
        }

    /** The other entry points send such a session to the default flow's error page, which is this one. */
    @Test
    fun `fetchSessionThenAnswerFailureOrRedirect - Fails a session whose flow is no longer configured`() = runTest {
        val session = ongoingInFlow(UUID.randomUUID())
        val invalidFlow = businessExceptionOf("flow.web.invalid_flow")
        val failed = failedSession(errorDetailsId = "flow.web.invalid_flow", errorDescriptionId = null)
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)
        every { interactiveAuthFlowSessionManager.findById("flow-id") } throws invalidFlow
        coEvery { sessionManager.markAsFailedIfNotRecoverable(session, invalidFlow) } returns failed
        coEvery { engine.advance(failed) } returns InteractiveFlowStepResult(failed, InteractiveFlowStep.Error)

        val result = answerErrorPage()

        assertEquals("flow.web.invalid_flow", result.detailsId)
        coVerify(exactly = 0) { engine.advance(session) }
    }

    @Test
    fun `fetchSessionThenAnswerFailureOrRedirect - Fails the session where completing it is refused`() = runTest {
        val session = ongoingInFlow(UUID.randomUUID())
        val refusal = businessExceptionOf("user.promote.identifier_taken")
        val failed = failedSession(errorDetailsId = "user.promote.identifier_taken", errorDescriptionId = null)
        stubFlowOf(session, mockk())
        coEvery { engine.advance(session) } throws refusal
        coEvery { sessionManager.markAsFailedIfNotRecoverable(session, refusal) } returns failed
        coEvery { engine.advance(failed) } returns InteractiveFlowStepResult(failed, InteractiveFlowStep.Error)

        val result = answerErrorPage()

        assertEquals("user.promote.identifier_taken", result.detailsId)
        coVerify(exactly = 0) { stepUriMapper.toRedirectUri(any(), any(), any()) }
    }

    /** Ask for the error page of the session `encoded-state` names, failing on a redirect. */
    private suspend fun answerErrorPage(): BusinessException = answerErrorPageWithSession().first

    @Test
    fun `fetchSessionThenLeave - Cancels a running session and sends the end-user where leaving it leads`() =
        runTest {
            val session = ongoing(UUID.randomUUID())
            val cancelled = mockk<CancelledInteractiveFlowSession>()
            val leaveUri = URI.create("https://client.example.com/account?sympauthy_flow=mfa_enrollment")
            every { session.handBack } returns plainHandBack
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                SuccessVerifyEncodedStateResult(session)
            coEvery { sessionManager.markAsCancelled(session) } returns cancelled
            coEvery { stepUriMapper.getLeaveUriOrNull(cancelled) } returns leaveUri

            assertEquals(leaveUri, util.fetchSessionThenLeave("encoded-state", observed))
        }

    @Test
    fun `fetchSessionThenLeave - Leaves a session that has ended as it is`() = runTest {
        val session = failedSession()
        val leaveUri = URI.create("https://client.example.com/callback?error=access_denied")
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)
        coEvery { stepUriMapper.getLeaveUriOrNull(session) } returns leaveUri

        assertEquals(leaveUri, util.fetchSessionThenLeave("encoded-state", observed))

        coVerify(exactly = 0) { sessionManager.markAsCancelled(any()) }
    }

    @Test
    fun `fetchSessionThenLeave - Sends the end-user where a session with no way out stands`() = runTest {
        val session = mockk<CompletedInteractiveFlowSession> { every { flowId } returns "flow-id" }
        val flow = mockk<InteractiveFlow>()
        val errorUri = URI.create("https://flow.example.com/error?state=encoded-state")
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)
        coEvery { stepUriMapper.getLeaveUriOrNull(session) } returns null
        coEvery { interactiveAuthFlowSessionManager.findById("flow-id") } returns flow
        coEvery { engine.advance(session) } returns
            InteractiveFlowStepResult(session, InteractiveFlowStep.AlreadyCompleted)
        coEvery { stepUriMapper.toRedirectUri(session, flow, InteractiveFlowStep.AlreadyCompleted) } returns errorUri

        assertEquals(errorUri, util.fetchSessionThenLeave("encoded-state", observed))
    }

    @Test
    fun `fetchSessionThenLeave - Shows the step a running session with nowhere to go stands on, advancing nothing`() =
        runTest {
            val session = ongoing(UUID.randomUUID())
            val flow = mockk<InteractiveFlow>()
            val stepUri = URI.create("https://flow.example.com/sign-in?state=encoded-state")
            every { session.handBack } returns null
            every { session.flowId } returns "flow-id"
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                SuccessVerifyEncodedStateResult(session)
            coEvery { stepUriMapper.getLeaveUriOrNull(session) } returns null
            coEvery { interactiveAuthFlowSessionManager.findById("flow-id") } returns flow
            coEvery { engine.currentStepOrNull(session) } returns InteractiveFlowStep.SignIn
            coEvery { stepUriMapper.toRedirectUri(session, flow, InteractiveFlowStep.SignIn) } returns stepUri

            assertEquals(stepUri, util.fetchSessionThenLeave("encoded-state", observed))

            coVerify(exactly = 0) { sessionManager.markAsCancelled(any()) }
            coVerify(exactly = 0) { engine.advance(any()) }
        }

    @Test
    fun `fetchSessionThenLeave - Carries a state resolving to no session to the error page`() = runTest {
        val flow = mockk<InteractiveFlow>()
        val errorUri = URI.create("https://flow.example.com/error?state=encoded-state")
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            FailedVerifyEncodedStateResult(detailsId = "auth.interactive_flow_session.validate.wrong_signature")
        coEvery { interactiveAuthFlowSessionManager.getDefaultInteractiveFlow() } returns flow
        every { stepUriMapper.getErrorUri("encoded-state", flow) } returns errorUri

        assertEquals(errorUri, util.fetchSessionThenLeave("encoded-state", observed))
    }

    /** Ask for the error page as [answerErrorPage] does, keeping the session the failure was answered for. */
    private suspend fun answerErrorPageWithSession(): Pair<BusinessException, InteractiveFlowSession?> =
        util.fetchSessionThenAnswerFailureOrRedirect(
            state = "encoded-state",
            observedRequest = observed,
            mapFailureToResource = { failure, session -> failure to session },
            mapRedirectUriToResource = { fail("Redirected to $it") }
        )

    private fun failedSession(
        errorDetailsId: String = "auth.interactive_flow_session.validate.expired",
        errorDescriptionId: String? = "description.oauth2.expired",
        errorValues: Map<String, String>? = null
    ) = FailedInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
        initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
        initiatingClientId = null,
        flowId = "flow-id",
        expirationDate = LocalDateTime.of(2026, 8, 31, 10, 0),
        startedBy = null,
        handBack = null,
        errorDetailsId = errorDetailsId,
        errorDescriptionId = errorDescriptionId,
        errorValues = errorValues,
        errorDate = LocalDateTime.of(2026, 8, 31, 10, 0),
    )

    private fun ongoingInFlow(sessionId: UUID) = mockk<OnGoingInteractiveFlowSession> {
        every { id } returns sessionId
        every { expired } returns false
        every { flowId } returns "flow-id"
    }

    private fun stubFlowOf(session: OnGoingInteractiveFlowSession, flow: InteractiveFlow) {
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)
        every { interactiveAuthFlowSessionManager.findById("flow-id") } returns flow
    }

    @Test
    fun `handleException - Returns the session unchanged when there is no exception`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()

        assertSame(session, util.handleException(session, null))
    }

    @Test
    fun `handleException - Rethrows a recoverable exception`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val error = recoverableBusinessExceptionOf("some.detail", "some.description")

        val thrown = assertThrows<BusinessException> { util.handleException(session, error) }
        assertSame(error, thrown)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `handleException - Routes a concurrent conflict by the current terminal session instead of failing it`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
        val completed = mockk<CompletedInteractiveFlowSession>()
        coEvery { sessionManager.fetchByIdOrNull(sessionId) } returns completed

        val result = util.handleException(session, businessExceptionOf(concurrentModification))

        // The concurrent winner completed the shared session, so this request is routed by that completed
        // session, never failed.
        assertSame(completed, result)
        coVerify(exactly = 0) { sessionManager.markAsFailedIfNotRecoverable(any(), any()) }
    }

    @Test
    fun `handleException - Fails the session when the concurrent conflict is still ongoing`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
        val stillOngoing = mockk<OnGoingInteractiveFlowSession>()
        val failed = mockk<FailedInteractiveFlowSession>()
        val error = businessExceptionOf(concurrentModification)
        coEvery { sessionManager.fetchByIdOrNull(sessionId) } returns stillOngoing
        coEvery { sessionManager.markAsFailedIfNotRecoverable(session, error) } returns failed

        val result = util.handleException(session, error)

        assertSame(failed, result)
    }

    @Test
    fun `handleException - Fails the session for a genuine non-recoverable error without re-fetching`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val failed = mockk<FailedInteractiveFlowSession>()
        val error = businessExceptionOf("flow.some.fatal")
        coEvery { sessionManager.markAsFailedIfNotRecoverable(session, error) } returns failed

        val result = util.handleException(session, error)

        assertSame(failed, result)
        coVerify(exactly = 0) { sessionManager.fetchByIdOrNull(any()) }
    }

    @Test
    fun `handleException - Writes the failure to the log before failing the session`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val failed = mockk<FailedInteractiveFlowSession>()
        val error = internalBusinessExceptionOf("manager.failed", IllegalStateException("Broken"))
        coEvery { sessionManager.markAsFailedIfNotRecoverable(session, error) } returns failed

        util.handleException(session, error)

        coVerifyOrder {
            exceptionHandler.record(error, error)
            sessionManager.markAsFailedIfNotRecoverable(session, error)
        }
    }

    @Test
    fun `handleException - Writes nothing for a recoverable exception`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()

        assertThrows<BusinessException> {
            util.handleException(session, recoverableBusinessExceptionOf("some.detail", "some.description"))
        }

        verify(exactly = 0) { exceptionHandler.record(any(), any()) }
    }

    @Test
    fun `handleException - Writes nothing for a failure the session is not failed with`() = runTest {
        val session = mockk<CompletedInteractiveFlowSession>()

        val result = util.handleException(session, internalBusinessExceptionOf("manager.failed"))

        assertSame(session, result)
        verify(exactly = 0) { exceptionHandler.record(any(), any()) }
    }
}

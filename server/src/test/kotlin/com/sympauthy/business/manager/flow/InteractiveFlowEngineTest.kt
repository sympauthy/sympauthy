package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.TerminalEffectResult
import java.net.URI
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class InteractiveFlowEngineTest {

    private val purposeRegistry = mockk<InteractiveFlowPurposeRegistry>()
    private val sessionManager = mockk<InteractiveFlowSessionManager>()
    private val engine = InteractiveFlowEngine(purposeRegistry, sessionManager)

    @Test
    fun `advance - Failed session maps to Error`() = runTest {
        val session = failedSession()

        val result = engine.advance(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.Error, result.step)
    }

    @Test
    fun `advance - Completed session maps to Complete`() = runTest {
        val session = completedSession()

        val result = engine.advance(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
    }

    @Test
    fun `advance - Cancelled session maps to Cancel`() = runTest {
        val session = cancelledSession()

        val result = engine.advance(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.Cancel, result.step)
    }

    @Test
    fun `advance - A purpose that still needs a step yields it`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.nextStepOrNull(session) } returns InteractiveFlowStep.SignIn

        val result = engine.advance(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.SignIn, result.step)
    }

    @Test
    fun `advance - All purposes resolved runs the terminal effect and completes the session`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val completed = completedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.nextStepOrNull(session) } returns null
        coEvery { handler.followUpPurposes(session) } returns emptyList()
        coEvery { handler.applyTerminalEffect(session) } returns TerminalEffectResult.Proceed
        coEvery { sessionManager.makePurposeAsComplete(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns completed

        val result = engine.advance(session)

        assertSame(completed, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
    }

    @Test
    fun `advance - A failing terminal effect fails the session and maps to Error`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val error = mockk<BusinessException>()
        val failed = failedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.nextStepOrNull(session) } returns null
        coEvery { handler.followUpPurposes(session) } returns emptyList()
        coEvery { handler.applyTerminalEffect(session) } returns TerminalEffectResult.Fail(error)
        coEvery { sessionManager.markAsFailedIfNotRecoverable(session, error) } returns failed

        val result = engine.advance(session)

        assertSame(failed, result.session)
        assertEquals(InteractiveFlowStep.Error, result.step)
    }

    @Test
    fun `advance - Appends a follow-up purpose then visits it`() = runTest {
        // The first purpose resolves and declares a follow-up; the engine appends it and the walk visits it.
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val grown = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val oauth2Handler = mockk<InteractiveFlowPurposeHandler>()
        val mfaHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns oauth2Handler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_CHALLENGE) } returns mfaHandler
        coEvery { oauth2Handler.nextStepOrNull(session) } returns null
        coEvery { oauth2Handler.followUpPurposes(session) } returns listOf(InteractiveFlowPurpose.MFA_CHALLENGE)
        coEvery {
            sessionManager.appendPurpose(session, InteractiveFlowPurpose.MFA_CHALLENGE)
        } returns grown
        coEvery { mfaHandler.nextStepOrNull(grown) } returns InteractiveFlowStep.MfaSelectionForChallenge

        val result = engine.advance(session)

        assertSame(grown, result.session)
        assertEquals(InteractiveFlowStep.MfaSelectionForChallenge, result.step)
    }

    @Test
    fun `advance - Does not re-append a follow-up purpose already present`() = runTest {
        // The follow-up is already on the session, so no append happens; the walk moves on to it.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val oauth2Handler = mockk<InteractiveFlowPurposeHandler>()
        val mfaHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns oauth2Handler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_CHALLENGE) } returns mfaHandler
        coEvery { oauth2Handler.nextStepOrNull(session) } returns null
        coEvery { oauth2Handler.followUpPurposes(session) } returns listOf(InteractiveFlowPurpose.MFA_CHALLENGE)
        coEvery { mfaHandler.nextStepOrNull(session) } returns InteractiveFlowStep.MfaSelectionForChallenge

        val result = engine.advance(session)

        assertEquals(InteractiveFlowStep.MfaSelectionForChallenge, result.step)
    }

    @Test
    fun `advance - Completes each purpose in order until the last yields a completed session`() = runTest {
        // Both purposes are resolved: each terminal effect runs in order, the first hands off a still-ongoing
        // session, the second completes it.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val afterFirst = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val completed = completedSession()
        val oauth2Handler = mockk<InteractiveFlowPurposeHandler>()
        val mfaHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns oauth2Handler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_CHALLENGE) } returns mfaHandler
        coEvery { oauth2Handler.nextStepOrNull(session) } returns null
        coEvery { oauth2Handler.followUpPurposes(session) } returns emptyList()
        coEvery { mfaHandler.nextStepOrNull(session) } returns null
        coEvery { mfaHandler.followUpPurposes(session) } returns emptyList()
        coEvery { oauth2Handler.applyTerminalEffect(session) } returns TerminalEffectResult.Proceed
        coEvery {
            sessionManager.makePurposeAsComplete(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        } returns afterFirst
        coEvery { mfaHandler.applyTerminalEffect(afterFirst) } returns TerminalEffectResult.Proceed
        coEvery {
            sessionManager.makePurposeAsComplete(afterFirst, InteractiveFlowPurpose.MFA_CHALLENGE)
        } returns completed

        val result = engine.advance(session)

        assertSame(completed, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
    }

    @Test
    fun `completeIfNecessary - Returns the session advanced by advance`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val completed = completedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.nextStepOrNull(session) } returns null
        coEvery { handler.followUpPurposes(session) } returns emptyList()
        coEvery { handler.applyTerminalEffect(session) } returns TerminalEffectResult.Proceed
        coEvery { sessionManager.makePurposeAsComplete(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns completed

        assertSame(completed, engine.completeIfNecessary(session))
    }

    private fun onGoingSession(purposes: List<InteractiveFlowPurpose>) = OnGoingInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = purposes,
        flowId = "flow-id",
        expirationDate = LocalDateTime.now().plusHours(1),
        sessionDate = LocalDateTime.now(),
        userId = null,
    )

    private fun completedSession() = CompletedInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
        flowId = "flow-id",
        expirationDate = LocalDateTime.now().plusHours(1),
        sessionDate = LocalDateTime.now(),
        userId = UUID.randomUUID(),
        completeDate = LocalDateTime.now(),
        successRedirectUri = URI.create("https://client.example.com/callback"),
        redirectType = InteractiveFlowRedirectType.AUTHORIZATION_CODE,
    )

    private fun cancelledSession() = CancelledInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
        flowId = "flow-id",
        expirationDate = LocalDateTime.now().plusHours(1),
        userId = UUID.randomUUID(),
        redirectType = InteractiveFlowRedirectType.AUTHORIZATION_CODE,
        successRedirectUri = URI.create("https://client.example.com/callback"),
        cancelRedirectUri = URI.create("https://client.example.com/callback"),
        cancelDate = LocalDateTime.now(),
    )

    private fun failedSession() = FailedInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
        flowId = "flow-id",
        expirationDate = LocalDateTime.now().plusHours(1),
        errorDetailsId = "error",
        errorDate = LocalDateTime.now(),
    )
}

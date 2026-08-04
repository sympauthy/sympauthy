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
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        val marked = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val completed = completedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        } returns marked
        coEvery { handler.followUpPurposes(marked) } returns emptyList()
        coEvery { handler.applyTerminalEffect(marked) } returns TerminalEffectResult.Proceed
        coEvery { sessionManager.markAsCompleted(marked) } returns completed

        val result = engine.advance(session)

        assertSame(completed, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
    }

    @Test
    fun `advance - A failing terminal effect fails the session and maps to Error`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val marked = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val error = mockk<BusinessException>()
        val failed = failedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        } returns marked
        coEvery { handler.followUpPurposes(marked) } returns emptyList()
        coEvery { handler.applyTerminalEffect(marked) } returns TerminalEffectResult.Fail(error)
        coEvery { sessionManager.markAsFailedIfNotRecoverable(marked, error) } returns failed

        val result = engine.advance(session)

        assertSame(failed, result.session)
        assertEquals(InteractiveFlowStep.Error, result.step)
    }

    @Test
    fun `advance - Marks a resolved purpose complete even when a later purpose still needs a step`() = runTest {
        // Regression: the CONFIRM gate resolves and must be recorded as completed even though the following
        // MFA purpose still yields a step (so the whole session is not yet complete and complete() is never
        // reached). `markPurposeAsCompleted` returning `afterConfirm` is what surfaces as the result session,
        // so reaching the assertion proves CONFIRM was marked complete as the engine handed off.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT)
        )
        val afterConfirm = onGoingSession(
            listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT)
        )
        val confirmHandler = mockk<InteractiveFlowPurposeHandler>()
        val mfaHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.CONFIRM) } returns confirmHandler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_ENROLLMENT) } returns mfaHandler
        coEvery { confirmHandler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.CONFIRM)
        } returns afterConfirm
        coEvery { confirmHandler.followUpPurposes(afterConfirm) } returns emptyList()
        coEvery { mfaHandler.nextStepOrNull(afterConfirm) } returns InteractiveFlowStep.MfaSelectionForEnrollment

        val result = engine.advance(session)

        assertSame(afterConfirm, result.session)
        assertEquals(InteractiveFlowStep.MfaSelectionForEnrollment, result.step)
    }

    @Test
    fun `advance - Inserts a follow-up purpose right after the resolving one then visits it`() = runTest {
        // The first purpose resolves and declares a follow-up; the engine inserts it right after and the walk
        // visits it.
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val marked = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val grown = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val oauth2Handler = mockk<InteractiveFlowPurposeHandler>()
        val mfaHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns oauth2Handler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_CHALLENGE) } returns mfaHandler
        coEvery { oauth2Handler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        } returns marked
        coEvery { oauth2Handler.followUpPurposes(marked) } returns listOf(InteractiveFlowPurpose.MFA_CHALLENGE)
        coEvery {
            sessionManager.insertPurposeAfter(
                marked,
                InteractiveFlowPurpose.MFA_CHALLENGE,
                InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            )
        } returns grown
        coEvery { mfaHandler.nextStepOrNull(grown) } returns InteractiveFlowStep.MfaSelectionForChallenge

        val result = engine.advance(session)

        assertSame(grown, result.session)
        assertEquals(InteractiveFlowStep.MfaSelectionForChallenge, result.step)
    }

    @Test
    fun `advance - Inserts a follow-up right after a mid-list gate, ahead of the purpose it guards`() = runTest {
        // REAUTHENTICATION is a *middle* gate: when it resolves and declares an MFA challenge, the engine must
        // insert the challenge right after it (before the trailing sensitive purpose), not at the list end, so
        // the guarded purpose (e.g. a provider link) can never run before the second factor. MFA_ENROLLMENT
        // stands in here for the trailing sensitive purpose.
        val session = onGoingSession(
            listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            ),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
        )
        val afterConfirm = onGoingSession(
            listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            ),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
        )
        val afterReauth = onGoingSession(
            listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            ),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
        )
        val grown = onGoingSession(
            listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.MFA_CHALLENGE,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            ),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
        )
        val confirmHandler = mockk<InteractiveFlowPurposeHandler>()
        val reauthHandler = mockk<InteractiveFlowPurposeHandler>()
        val mfaChallengeHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.CONFIRM) } returns confirmHandler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.REAUTHENTICATION) } returns reauthHandler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_CHALLENGE) } returns mfaChallengeHandler
        coEvery { confirmHandler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.CONFIRM)
        } returns afterConfirm
        coEvery { confirmHandler.followUpPurposes(afterConfirm) } returns emptyList()
        coEvery { reauthHandler.nextStepOrNull(afterConfirm) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(afterConfirm, InteractiveFlowPurpose.REAUTHENTICATION)
        } returns afterReauth
        coEvery {
            reauthHandler.followUpPurposes(afterReauth)
        } returns listOf(InteractiveFlowPurpose.MFA_CHALLENGE)
        coEvery {
            sessionManager.insertPurposeAfter(
                afterReauth,
                InteractiveFlowPurpose.MFA_CHALLENGE,
                InteractiveFlowPurpose.REAUTHENTICATION,
            )
        } returns grown
        coEvery {
            mfaChallengeHandler.nextStepOrNull(grown)
        } returns InteractiveFlowStep.MfaSelectionForChallenge

        val result = engine.advance(session)

        assertSame(grown, result.session)
        assertEquals(InteractiveFlowStep.MfaSelectionForChallenge, result.step)
    }

    @Test
    fun `advance - Inserts a new follow-up after an already-present sibling, keeping declared order`() = runTest {
        // A handler declaring several follow-ups [alreadyPresent, new] must keep their declared order: the new
        // one lands right after the already-present sibling, not back at the resolving purpose. Here
        // OAUTH2_AUTHORIZE resolves and declares [MFA_ENROLLMENT (already present), MFA_CHALLENGE (new)], so
        // MFA_CHALLENGE must be inserted after MFA_ENROLLMENT.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_ENROLLMENT)
        )
        val marked = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_ENROLLMENT)
        )
        val grown = onGoingSession(
            listOf(
                InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
                InteractiveFlowPurpose.MFA_CHALLENGE,
            )
        )
        val oauth2Handler = mockk<InteractiveFlowPurposeHandler>()
        val mfaEnrollmentHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns oauth2Handler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_ENROLLMENT) } returns mfaEnrollmentHandler
        coEvery { oauth2Handler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        } returns marked
        coEvery { oauth2Handler.followUpPurposes(marked) } returns listOf(
            InteractiveFlowPurpose.MFA_ENROLLMENT,
            InteractiveFlowPurpose.MFA_CHALLENGE,
        )
        coEvery {
            sessionManager.insertPurposeAfter(
                marked,
                InteractiveFlowPurpose.MFA_CHALLENGE,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            )
        } returns grown
        coEvery {
            mfaEnrollmentHandler.nextStepOrNull(grown)
        } returns InteractiveFlowStep.MfaSelectionForEnrollment

        val result = engine.advance(session)

        assertEquals(InteractiveFlowStep.MfaSelectionForEnrollment, result.step)
        // The new follow-up is inserted after the already-present sibling, not after the resolving purpose.
        coVerify {
            sessionManager.insertPurposeAfter(
                marked,
                InteractiveFlowPurpose.MFA_CHALLENGE,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            )
        }
    }

    @Test
    fun `advance - Does not re-insert a follow-up purpose already present`() = runTest {
        // The follow-up is already on the session, so no insert happens; the walk moves on to it.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val marked = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val oauth2Handler = mockk<InteractiveFlowPurposeHandler>()
        val mfaHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns oauth2Handler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_CHALLENGE) } returns mfaHandler
        coEvery { oauth2Handler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        } returns marked
        coEvery { oauth2Handler.followUpPurposes(marked) } returns listOf(InteractiveFlowPurpose.MFA_CHALLENGE)
        coEvery { mfaHandler.nextStepOrNull(marked) } returns InteractiveFlowStep.MfaSelectionForChallenge

        val result = engine.advance(session)

        assertEquals(InteractiveFlowStep.MfaSelectionForChallenge, result.step)
    }

    @Test
    fun `advance - Runs every terminal effect in order then completes the session once`() = runTest {
        // Both purposes are resolved: each is marked complete as the engine hands off, then every terminal
        // effect runs in order on the fully-marked session before the single completion transition.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val afterFirst = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val afterSecond = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE)
        )
        val completed = completedSession()
        val oauth2Handler = mockk<InteractiveFlowPurposeHandler>()
        val mfaHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns oauth2Handler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.MFA_CHALLENGE) } returns mfaHandler
        coEvery { oauth2Handler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        } returns afterFirst
        coEvery { oauth2Handler.followUpPurposes(afterFirst) } returns emptyList()
        coEvery { mfaHandler.nextStepOrNull(afterFirst) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(afterFirst, InteractiveFlowPurpose.MFA_CHALLENGE)
        } returns afterSecond
        coEvery { mfaHandler.followUpPurposes(afterSecond) } returns emptyList()
        coEvery { oauth2Handler.applyTerminalEffect(afterSecond) } returns TerminalEffectResult.Proceed
        coEvery { mfaHandler.applyTerminalEffect(afterSecond) } returns TerminalEffectResult.Proceed
        coEvery { sessionManager.markAsCompleted(afterSecond) } returns completed

        val result = engine.advance(session)

        assertSame(completed, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
        // Terminal effects run on the fully-marked session, in purpose order, before completion.
        coVerifyOrder {
            oauth2Handler.applyTerminalEffect(afterSecond)
            mfaHandler.applyTerminalEffect(afterSecond)
        }
    }

    @Test
    fun `currentPurposeOrNull - Returns the first purpose that still needs a step`() = runTest {
        // CONFIRM has resolved (null step) but REAUTHENTICATION still needs the sign-in step.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.REAUTHENTICATION)
        )
        val confirmHandler = mockk<InteractiveFlowPurposeHandler>()
        val reauthHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.CONFIRM) } returns confirmHandler
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.REAUTHENTICATION) } returns reauthHandler
        coEvery { confirmHandler.nextStepOrNull(session) } returns null
        coEvery { reauthHandler.nextStepOrNull(session) } returns InteractiveFlowStep.SignIn

        assertEquals(InteractiveFlowPurpose.REAUTHENTICATION, engine.currentPurposeOrNull(session))
    }

    @Test
    fun `currentPurposeOrNull - Stops at the earliest unresolved purpose`() = runTest {
        // The first purpose already yields a step, so later handlers are never consulted.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.REAUTHENTICATION)
        )
        val confirmHandler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.CONFIRM) } returns confirmHandler
        coEvery { confirmHandler.nextStepOrNull(session) } returns InteractiveFlowStep.Confirm

        assertEquals(InteractiveFlowPurpose.CONFIRM, engine.currentPurposeOrNull(session))
    }

    @Test
    fun `currentPurposeOrNull - Returns null when every purpose has resolved`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.nextStepOrNull(session) } returns null

        assertNull(engine.currentPurposeOrNull(session))
    }

    @Test
    fun `completeIfNecessary - Returns the session advanced by advance`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val marked = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val completed = completedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.nextStepOrNull(session) } returns null
        coEvery {
            sessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        } returns marked
        coEvery { handler.followUpPurposes(marked) } returns emptyList()
        coEvery { handler.applyTerminalEffect(marked) } returns TerminalEffectResult.Proceed
        coEvery { sessionManager.markAsCompleted(marked) } returns completed

        assertSame(completed, engine.completeIfNecessary(session))
    }

    private fun onGoingSession(
        purposes: List<InteractiveFlowPurpose>,
        initiatingPurpose: InteractiveFlowPurpose = purposes.first(),
    ) = OnGoingInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = purposes,
        initiatingPurpose = initiatingPurpose,
        flowId = "flow-id",
        expirationDate = LocalDateTime.now().plusHours(1),
        sessionDate = LocalDateTime.now(),
        userId = null,
    )

    private fun completedSession() = CompletedInteractiveFlowSession(
        id = UUID.randomUUID(),
        purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
        initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
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
        initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
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
        initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
        flowId = "flow-id",
        expirationDate = LocalDateTime.now().plusHours(1),
        errorDetailsId = "error",
        errorDate = LocalDateTime.now(),
    )
}

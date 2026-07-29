package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStepResult
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
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
    private val engine = InteractiveFlowEngine(purposeRegistry)

    @Test
    fun `getCurrentStep - Failed session maps to Error`() = runTest {
        val session = failedSession()

        val result = engine.getCurrentStep(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.Error, result.step)
    }

    @Test
    fun `getCurrentStep - Completed session maps to Complete`() = runTest {
        val session = completedSession()

        val result = engine.getCurrentStep(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
    }

    @Test
    fun `getCurrentStep - Pending purpose yields its step`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.getNextStep(session) } returns
            InteractiveFlowPurposeStepResult.Pending(session, InteractiveFlowStep.SignIn)

        val result = engine.getCurrentStep(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.SignIn, result.step)
    }

    @Test
    fun `getCurrentStep - All purposes resolved runs the initiating complete and maps Completed to Complete`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val completed = completedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.getNextStep(session) } returns InteractiveFlowPurposeStepResult.Resolved(session)
        coEvery { handler.complete(session) } returns completed

        val result = engine.getCurrentStep(session)

        assertSame(completed, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
    }

    @Test
    fun `getCurrentStep - Initiating complete returning Failed maps to Error`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val failed = failedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.getNextStep(session) } returns InteractiveFlowPurposeStepResult.Resolved(session)
        coEvery { handler.complete(session) } returns failed

        val result = engine.getCurrentStep(session)

        assertSame(failed, result.session)
        assertEquals(InteractiveFlowStep.Error, result.step)
    }

    @Test
    fun `getCurrentStep - Walks to the next purpose once the first resolves`() = runTest {
        // Two purposes in the list: the first resolves, the second is pending and yields the step.
        val session = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        )
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.getNextStep(session) } returnsMany listOf(
            InteractiveFlowPurposeStepResult.Resolved(session),
            InteractiveFlowPurposeStepResult.Pending(session, InteractiveFlowStep.MfaSelectionForEnrollment)
        )

        val result = engine.getCurrentStep(session)

        assertEquals(InteractiveFlowStep.MfaSelectionForEnrollment, result.step)
    }

    @Test
    fun `getCurrentStep - Visits a purpose appended while resolving`() = runTest {
        // The first purpose resolves into a session that grew by one purpose; that appended purpose is visited.
        val initial = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val grown = onGoingSession(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        )
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.getNextStep(initial) } returns InteractiveFlowPurposeStepResult.Resolved(grown)
        coEvery { handler.getNextStep(grown) } returns
            InteractiveFlowPurposeStepResult.Pending(grown, InteractiveFlowStep.MfaSelectionForEnrollment)

        val result = engine.getCurrentStep(initial)

        assertSame(grown, result.session)
        assertEquals(InteractiveFlowStep.MfaSelectionForEnrollment, result.step)
    }

    @Test
    fun `completeIfNecessary - Returns the session resolved by getCurrentStep`() = runTest {
        val session = onGoingSession(listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        val completed = completedSession()
        val handler = mockk<InteractiveFlowPurposeHandler>()
        every { purposeRegistry.getForPurpose(InteractiveFlowPurpose.OAUTH2_AUTHORIZE) } returns handler
        coEvery { handler.getNextStep(session) } returns InteractiveFlowPurposeStepResult.Resolved(session)
        coEvery { handler.complete(session) } returns completed

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

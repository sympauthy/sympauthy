package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStepResult
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class MfaChallengeInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @InjectMockKs
    lateinit var handler: MfaChallengeInteractiveFlowPurposeHandler

    @Test
    fun `getNextStep - Returns Pending MfaSelectionForChallenge when MFA not yet passed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { mfaPassed } returns false }

        val result = handler.getNextStep(session)

        val pending = assertInstanceOf(InteractiveFlowPurposeStepResult.Pending::class.java, result)
        assertEquals(InteractiveFlowStep.MfaSelectionForChallenge, pending.step)
    }

    @Test
    fun `getNextStep - Returns Resolved once MFA passed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { mfaPassed } returns true }

        val result = handler.getNextStep(session)

        assertInstanceOf(InteractiveFlowPurposeStepResult.Resolved::class.java, result)
        assertSame(session, result.session)
    }

    @Test
    fun `complete - Marks the challenge purpose complete`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val completed = mockk<CompletedInteractiveFlowSession>()
        coEvery {
            sessionManager.makePurposeAsComplete(session, InteractiveFlowPurpose.MFA_CHALLENGE)
        } returns completed

        assertSame(completed, handler.complete(session))
    }
}

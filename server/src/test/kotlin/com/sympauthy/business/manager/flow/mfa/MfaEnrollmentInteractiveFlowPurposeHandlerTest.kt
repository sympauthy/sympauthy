package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
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
class MfaEnrollmentInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @InjectMockKs
    lateinit var handler: MfaEnrollmentInteractiveFlowPurposeHandler

    @Test
    fun `getNextStep - Returns Pending MfaTotpEnroll when MFA not yet passed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { mfaPassed } returns false }

        val result = handler.getNextStep(session)

        val pending = assertInstanceOf(InteractiveFlowPurposeStepResult.Pending::class.java, result)
        assertEquals(InteractiveFlowStep.MfaTotpEnroll, pending.step)
    }

    @Test
    fun `getNextStep - Returns Resolved once MFA passed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { mfaPassed } returns true }

        val result = handler.getNextStep(session)

        assertInstanceOf(InteractiveFlowPurposeStepResult.Resolved::class.java, result)
        assertSame(session, result.session)
    }

    @Test
    fun `complete - Marks the session complete`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val completed = mockk<CompletedInteractiveFlowSession>()
        coEvery { sessionManager.markAsComplete(session) } returns completed

        assertSame(completed, handler.complete(session))
    }
}

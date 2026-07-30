package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MfaEnrollmentInteractiveFlowPurposeHandlerTest {

    private val handler = MfaEnrollmentInteractiveFlowPurposeHandler()

    @Test
    fun `nextStepOrNull - Returns MfaSelectionForEnrollment when MFA not yet passed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { mfaPassed } returns false }

        assertEquals(InteractiveFlowStep.MfaSelectionForEnrollment, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Returns null once MFA passed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { mfaPassed } returns true }

        assertNull(handler.nextStepOrNull(session))
    }
}

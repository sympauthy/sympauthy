package com.sympauthy.business.manager.flow.confirm

import com.sympauthy.business.model.flow.InteractiveFlowSessionConfirm
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class ConfirmInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var confirmManager: InteractiveFlowSessionConfirmManager

    @InjectMockKs
    lateinit var handler: ConfirmInteractiveFlowPurposeHandler

    private val session = mockk<OnGoingInteractiveFlowSession>()

    @Test
    fun `nextStepOrNull - Returns Confirm when the session carries no confirm record`() = runTest {
        coEvery { confirmManager.fetchConfirmOrNull(session) } returns null

        assertEquals(InteractiveFlowStep.Confirm, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Returns Confirm while the confirmation is still pending`() = runTest {
        val confirm = mockk<InteractiveFlowSessionConfirm> { coEvery { confirmed } returns false }
        coEvery { confirmManager.fetchConfirmOrNull(session) } returns confirm

        assertEquals(InteractiveFlowStep.Confirm, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Returns null once the end-user has confirmed`() = runTest {
        val confirm = mockk<InteractiveFlowSessionConfirm> { coEvery { confirmed } returns true }
        coEvery { confirmManager.fetchConfirmOrNull(session) } returns confirm

        assertNull(handler.nextStepOrNull(session))
    }
}

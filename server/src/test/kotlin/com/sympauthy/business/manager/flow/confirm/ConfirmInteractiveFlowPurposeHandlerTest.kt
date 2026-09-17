package com.sympauthy.business.manager.flow.confirm

import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionConfirm
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
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

    @Test
    fun `debugInformation - Names the action, the client that asked and when it was approved`() = runTest {
        val confirmedDate = LocalDateTime.now()
        coEvery { confirmManager.fetchConfirmOrNull(session) } returns InteractiveFlowSessionConfirm(
            sessionId = UUID.randomUUID(),
            action = ConfirmActionType.LINK_PROVIDER,
            clientId = "web-app",
            confirmedDate = confirmedDate
        )

        assertEquals(
            listOf(
                PurposeDebugInformation("Action", "link_provider"),
                PurposeDebugInformation("Initiated by", "web-app"),
                PurposeDebugInformation("Confirmed date", confirmedDate.toString())
            ),
            handler.debugInformation(session)
        )
    }

    @Test
    fun `debugInformation - Says an administrator asked when the record names no client`() = runTest {
        coEvery { confirmManager.fetchConfirmOrNull(session) } returns InteractiveFlowSessionConfirm(
            sessionId = UUID.randomUUID(),
            action = ConfirmActionType.ENROLL_MFA,
            clientId = null,
            confirmedDate = null
        )

        val information = handler.debugInformation(session)

        assertEquals("an administrator", information.first { it.displayName == "Initiated by" }.value)
        assertNull(information.first { it.displayName == "Confirmed date" }.value)
    }

    @Test
    fun `debugInformation - Emits every label with no value when the session carries no confirm record`() =
        runTest {
            coEvery { confirmManager.fetchConfirmOrNull(session) } returns null

            val information = handler.debugInformation(session)

            assertEquals(3, information.size)
            assertTrue(information.all { it.value == null })
        }

    @Test
    fun `debugInformation - Answers for a session that has no user and reached a terminal status`() = runTest {
        val failed = mockk<FailedInteractiveFlowSession>()
        coEvery { confirmManager.fetchConfirmOrNull(failed) } returns null

        assertEquals(3, handler.debugInformation(failed).size)
    }
}

package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.CancelledInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.business.model.mfa.MfaMethodType
import io.mockk.coEvery
import io.mockk.every
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
class MfaChallengeInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @InjectMockKs
    lateinit var handler: MfaChallengeInteractiveFlowPurposeHandler

    @Test
    fun `nextStepOrNull - Returns MfaSelectionForChallenge when MFA not yet passed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { mfaPassed } returns false }

        assertEquals(InteractiveFlowStep.MfaSelectionForChallenge, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Returns null once MFA passed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> { every { mfaPassed } returns true }

        assertNull(handler.nextStepOrNull(session))
    }

    @Test
    fun `debugInformation - Names when MFA was passed and what there is to challenge with`() = runTest {
        val userId = UUID.randomUUID()
        val mfaPassedDate = LocalDateTime.now()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { this@mockk.mfaPassedDate } returns mfaPassedDate
        }
        coEvery { totpManager.findEnrolledMethodTypes(userId) } returns listOf(MfaMethodType.TOTP)

        assertEquals(
            listOf(
                PurposeDebugInformation.Date("mfa_passed_at", mfaPassedDate),
                PurposeDebugInformation.TextList("challengeable_methods", listOf("totp"))
            ),
            handler.debugInformation(session)
        )
    }

    @Test
    fun `debugInformation - Says no method where the account has enrolled nothing, which the stall looks like`() =
        runTest {
            val userId = UUID.randomUUID()
            val session = mockk<OnGoingInteractiveFlowSession> {
                every { this@mockk.userId } returns userId
                every { mfaPassedDate } returns null
            }
            coEvery { totpManager.findEnrolledMethodTypes(userId) } returns emptyList()

            val information = handler.debugInformation(session)

            assertEquals(emptyList<String>(), information.first { it.code == "challengeable_methods" }.value)
        }

    @Test
    fun `debugInformation - Answers for a session with no user`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { userId } returns null
            every { mfaPassedDate } returns null
        }

        val information = handler.debugInformation(session)

        assertEquals(listOf("mfa_passed_at", "challengeable_methods"), information.map { it.code })
        assertTrue(information.all { it.value == null })
    }

    @Test
    fun `debugInformation - Answers for a session that reached a terminal status`() = runTest {
        val cancelled = mockk<CancelledInteractiveFlowSession> { every { userId } returns null }

        val information = handler.debugInformation(cancelled)

        assertEquals(listOf("mfa_passed_at", "challengeable_methods"), information.map { it.code })
        assertTrue(information.all { it.value == null })
    }
}

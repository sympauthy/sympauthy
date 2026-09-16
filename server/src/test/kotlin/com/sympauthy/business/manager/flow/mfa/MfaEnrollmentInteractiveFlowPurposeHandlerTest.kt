package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.business.model.mfa.TotpEnrollment
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class MfaEnrollmentInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @InjectMockKs
    lateinit var handler: MfaEnrollmentInteractiveFlowPurposeHandler

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

    @Test
    fun `debugInformation - Names the enrolled method and when this session passed MFA`() = runTest {
        val userId = UUID.randomUUID()
        val mfaPassedDate = LocalDateTime.now()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { this@mockk.mfaPassedDate } returns mfaPassedDate
        }
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(enrollment(userId))

        assertEquals(
            listOf(
                PurposeDebugInformation("Enrolled methods", "totp"),
                PurposeDebugInformation("MFA passed date", mfaPassedDate.toString())
            ),
            handler.debugInformation(session)
        )
    }

    @Test
    fun `debugInformation - Emits no method when the account has enrolled none`() = runTest {
        val userId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { mfaPassedDate } returns null
        }
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()

        assertTrue(handler.debugInformation(session).all { it.value == null })
    }

    @Test
    fun `debugInformation - Never emits the secret behind an enrollment`() = runTest {
        val userId = UUID.randomUUID()
        val secret = "a-secret-nobody-may-read"
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { mfaPassedDate } returns LocalDateTime.now()
        }
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns
            listOf(enrollment(userId, secret.toByteArray()))

        val emitted = handler.debugInformation(session).mapNotNull { it.value }

        assertFalse(emitted.any { it.contains(secret) })
        assertFalse(emitted.any { it.contains(secret.toByteArray().contentToString()) })
    }

    @Test
    fun `debugInformation - Answers for a session with no user`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { userId } returns null
            every { mfaPassedDate } returns null
        }

        assertEquals(2, handler.debugInformation(session).size)
    }

    @Test
    fun `debugInformation - Answers for a session that reached a terminal status`() = runTest {
        assertTrue(handler.debugInformation(mockk<FailedInteractiveFlowSession>()).all { it.value == null })
    }

    private fun enrollment(userId: UUID, secret: ByteArray = ByteArray(20)) = TotpEnrollment(
        id = UUID.randomUUID(),
        userId = userId,
        secret = secret,
        creationDate = LocalDateTime.now().minusMinutes(5),
        confirmedDate = LocalDateTime.now()
    )
}

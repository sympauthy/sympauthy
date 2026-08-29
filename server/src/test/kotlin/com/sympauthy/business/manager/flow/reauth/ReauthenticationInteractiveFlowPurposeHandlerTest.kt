package com.sympauthy.business.manager.flow.reauth

import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionReauthentication
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.config.model.EnabledMfaConfig
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
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class ReauthenticationInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var reauthenticationManager: InteractiveFlowSessionReauthenticationManager

    @MockK
    lateinit var uncheckedMfaConfig: EnabledMfaConfig

    @MockK
    lateinit var totpManager: TotpManager

    @InjectMockKs
    lateinit var handler: ReauthenticationInteractiveFlowPurposeHandler

    private val session = mockk<OnGoingInteractiveFlowSession>()

    // --- nextStepOrNull ---

    @Test
    fun `nextStepOrNull - Returns SignIn when the session carries no re-authentication record`() = runTest {
        coEvery { reauthenticationManager.fetchReauthenticationOrNull(session) } returns null

        assertEquals(InteractiveFlowStep.SignIn, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Returns SignIn while the primary credential is not proven`() = runTest {
        val record = mockk<InteractiveFlowSessionReauthentication> {
            every { primaryCredentialProven } returns false
        }
        coEvery { reauthenticationManager.fetchReauthenticationOrNull(session) } returns record

        assertEquals(InteractiveFlowStep.SignIn, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Returns null once the primary credential is proven`() = runTest {
        val record = mockk<InteractiveFlowSessionReauthentication> {
            every { primaryCredentialProven } returns true
        }
        coEvery { reauthenticationManager.fetchReauthenticationOrNull(session) } returns record

        assertNull(handler.nextStepOrNull(session))
    }

    // --- followUpPurposes ---

    @Test
    fun `followUpPurposes - Appends MFA_CHALLENGE when MFA is enabled and the account is enrolled`() = runTest {
        val userId = UUID.randomUUID()
        every { session.purposes } returns listOf(InteractiveFlowPurpose.REAUTHENTICATION)
        every { session.userId } returns userId
        every { uncheckedMfaConfig.enabled } returns true
        coEvery { totpManager.isEnrolled(userId) } returns true

        assertEquals(listOf(InteractiveFlowPurpose.MFA_CHALLENGE), handler.followUpPurposes(session))
    }

    @Test
    fun `followUpPurposes - Empty when the account is not enrolled`() = runTest {
        val userId = UUID.randomUUID()
        every { session.purposes } returns listOf(InteractiveFlowPurpose.REAUTHENTICATION)
        every { session.userId } returns userId
        every { uncheckedMfaConfig.enabled } returns true
        coEvery { totpManager.isEnrolled(userId) } returns false

        assertTrue(handler.followUpPurposes(session).isEmpty())
    }

    @Test
    fun `followUpPurposes - Empty when MFA is disabled`() = runTest {
        every { session.purposes } returns listOf(InteractiveFlowPurpose.REAUTHENTICATION)
        every { uncheckedMfaConfig.enabled } returns false

        assertTrue(handler.followUpPurposes(session).isEmpty())
    }

    @Test
    fun `followUpPurposes - Empty when an MFA purpose is already present`() = runTest {
        // uncheckedMfaConfig / totpManager are left unstubbed: reaching the assertion proves the handler
        // short-circuited on the already-present MFA purpose before consulting them.
        every { session.purposes } returns listOf(
            InteractiveFlowPurpose.REAUTHENTICATION,
            InteractiveFlowPurpose.MFA_CHALLENGE
        )

        assertTrue(handler.followUpPurposes(session).isEmpty())
    }

    @Test
    fun `followUpPurposes - Empty when the session has no user`() = runTest {
        every { session.purposes } returns listOf(InteractiveFlowPurpose.REAUTHENTICATION)
        every { session.userId } returns null
        every { uncheckedMfaConfig.enabled } returns true

        assertTrue(handler.followUpPurposes(session).isEmpty())
    }
}

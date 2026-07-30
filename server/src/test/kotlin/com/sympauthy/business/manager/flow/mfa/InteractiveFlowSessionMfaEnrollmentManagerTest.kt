package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMfaEnrollmentMapper
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionMfaEnrollment
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.data.model.InteractiveFlowSessionMfaEnrollmentEntity
import com.sympauthy.data.repository.InteractiveFlowSessionMfaEnrollmentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionMfaEnrollmentManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var mfaEnrollmentRepository: InteractiveFlowSessionMfaEnrollmentRepository

    @MockK
    lateinit var mfaEnrollmentMapper: InteractiveFlowSessionMfaEnrollmentMapper

    @MockK
    lateinit var totpManager: TotpManager

    private val userId = UUID.randomUUID()
    private val session = mockk<OnGoingInteractiveFlowSession>()

    private fun managerWith(mfaConfig: MfaConfig) = InteractiveFlowSessionMfaEnrollmentManager(
        sessionManager = sessionManager,
        mfaEnrollmentRepository = mfaEnrollmentRepository,
        mfaEnrollmentMapper = mfaEnrollmentMapper,
        uncheckedMfaConfig = mfaConfig,
        totpManager = totpManager
    )

    private val optionalMfa = EnabledMfaConfig(totp = true, required = false)
    private val requiredMfa = EnabledMfaConfig(totp = true, required = true)

    // --- startMfaEnrollmentSession / fetch ---

    @Test
    fun `startMfaEnrollmentSession - Creates the session, sets the user, and saves the return URI`() = runTest {
        val manager = managerWith(optionalMfa)
        val sessionId = UUID.randomUUID()
        val returnUri = URI.create("https://client.example.com/enrolled")
        val flow = mockk<AuthorizationFlow>()
        val newSession = mockk<OnGoingInteractiveFlowSession>()
        val withUser = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
        val expectedEntity = InteractiveFlowSessionMfaEnrollmentEntity(
            sessionId = sessionId,
            returnUri = returnUri.toString()
        )
        coEvery {
            sessionManager.newSession(listOf(InteractiveFlowPurpose.MFA_ENROLLMENT), flow)
        } returns newSession
        coEvery { sessionManager.setAuthenticatedUserId(newSession, userId) } returns withUser
        coEvery { mfaEnrollmentRepository.save(expectedEntity) } returnsArgument 0

        val result = manager.startMfaEnrollmentSession(userId, returnUri, flow)

        assertSame(withUser, result)
    }

    @Test
    fun `fetchMfaEnrollmentOrNull - Returns the mapped record when present`() = runTest {
        val manager = managerWith(optionalMfa)
        val sessionId = UUID.randomUUID()
        val session = mockk<InteractiveFlowSession> { every { id } returns sessionId }
        val entity = InteractiveFlowSessionMfaEnrollmentEntity(sessionId, "https://client.example.com/done")
        val record = InteractiveFlowSessionMfaEnrollment(sessionId, "https://client.example.com/done")
        coEvery { mfaEnrollmentRepository.findBySessionId(sessionId) } returns entity
        every { mfaEnrollmentMapper.toInteractiveFlowSessionMfaEnrollment(entity) } returns record

        assertSame(record, manager.fetchMfaEnrollmentOrNull(session))
    }

    @Test
    fun `fetchMfaEnrollment - Throws when no record is attached`() = runTest {
        val manager = managerWith(optionalMfa)
        val sessionId = UUID.randomUUID()
        val session = mockk<InteractiveFlowSession> { every { id } returns sessionId }
        coEvery { mfaEnrollmentRepository.findBySessionId(sessionId) } returns null

        val exception = assertThrows<BusinessException> {
            manager.fetchMfaEnrollment(session)
        }
        assertEquals("flow.mfa.enrollment.missing", exception.detailsId)
    }

    // --- getEnrollmentRoutingResult ---

    @Test
    fun `getEnrollmentRoutingResult - optional and not standalone - offers TOTP enrollment with skip`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE

        val result = manager.getEnrollmentRoutingResult(session)

        assertEquals(
            MfaMethodSelection(
                methods = listOf(AvailableMfaMethod(name = "TOTP", step = InteractiveFlowStep.MfaTotpEnroll)),
                skippable = true
            ),
            result
        )
    }

    @Test
    fun `getEnrollmentRoutingResult - required - auto-redirects to TOTP enrollment`() = runTest {
        val manager = managerWith(requiredMfa)

        val result = manager.getEnrollmentRoutingResult(session)

        assertEquals(MfaAutoRedirect(InteractiveFlowStep.MfaTotpEnroll), result)
    }

    @Test
    fun `getEnrollmentRoutingResult - standalone - auto-redirects to TOTP enrollment`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT

        val result = manager.getEnrollmentRoutingResult(session)

        assertEquals(MfaAutoRedirect(InteractiveFlowStep.MfaTotpEnroll), result)
    }

    // --- isEnrollmentSkippable ---

    @Test
    fun `isEnrollmentSkippable - false when MFA is required`() = runTest {
        val manager = managerWith(requiredMfa)

        assertFalse(manager.isEnrollmentSkippable(session))
    }

    @Test
    fun `isEnrollmentSkippable - false for a standalone enrollment`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT

        assertFalse(manager.isEnrollmentSkippable(session))
    }

    @Test
    fun `isEnrollmentSkippable - true when optional and not standalone`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE

        assertTrue(manager.isEnrollmentSkippable(session))
    }

    // --- skipMfa ---

    @Test
    fun `skipMfa - Sets mfaPassed and returns updated session when optional and not enrolled`() = runTest {
        val manager = managerWith(optionalMfa)
        val updatedSession = mockk<OnGoingInteractiveFlowSession>()

        every { session.initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        every { session.userId } returns userId
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns emptyList()
        coEvery { sessionManager.setMfaPassed(session) } returns updatedSession

        assertSame(updatedSession, manager.skipMfa(session))
    }

    @Test
    fun `skipMfa - Throws when MFA is required`() = runTest {
        val manager = managerWith(requiredMfa)

        val exception = assertThrows<BusinessException> { manager.skipMfa(session) }

        assertEquals("flow.mfa.skip.not_allowed", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    @Test
    fun `skipMfa - Throws for a standalone enrollment`() = runTest {
        val manager = managerWith(optionalMfa)
        every { session.initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT

        val exception = assertThrows<BusinessException> { manager.skipMfa(session) }

        assertEquals("flow.mfa.skip.not_allowed_standalone", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    @Test
    fun `skipMfa - Throws when optional but user is enrolled`() = runTest {
        val manager = managerWith(optionalMfa)

        every { session.initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        every { session.userId } returns userId
        coEvery { totpManager.findConfirmedEnrollments(userId) } returns listOf(mockk())

        val exception = assertThrows<BusinessException> { manager.skipMfa(session) }

        assertEquals("flow.mfa.skip.not_allowed_when_enrolled", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }
}

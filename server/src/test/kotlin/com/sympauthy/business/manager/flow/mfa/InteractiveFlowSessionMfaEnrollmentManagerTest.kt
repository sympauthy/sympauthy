package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMfaEnrollmentMapper
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionMfaEnrollment
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionMfaEnrollmentEntity
import com.sympauthy.data.repository.InteractiveFlowSessionMfaEnrollmentRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
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

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionMfaEnrollmentManager

    @Test
    fun `startMfaEnrollmentSession - Creates the session, sets the user, and saves the return URI`() = runTest {
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val returnUri = URI.create("https://client.example.com/enrolled")
        val flow = mockk<AuthorizationFlow>()
        val newSession = mockk<OnGoingInteractiveFlowSession>()
        val withUser = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
        // Stub with the exact entity so reaching the assertion proves the right return URI was persisted.
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
        val sessionId = UUID.randomUUID()
        val session = mockk<InteractiveFlowSession> { every { id } returns sessionId }
        coEvery { mfaEnrollmentRepository.findBySessionId(sessionId) } returns null

        val exception = assertThrows<BusinessException> {
            manager.fetchMfaEnrollment(session)
        }
        assertEquals("flow.mfa.enrollment.missing", exception.detailsId)
    }
}

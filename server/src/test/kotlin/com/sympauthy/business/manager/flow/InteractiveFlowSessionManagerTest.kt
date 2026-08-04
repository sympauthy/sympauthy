package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMapper
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionManagerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var sessionRepository: InteractiveFlowSessionRepository

    @MockK
    lateinit var sessionMapper: InteractiveFlowSessionMapper

    @MockK
    lateinit var uncheckedAuthConfig: AuthConfig

    @SpyK
    @InjectMockKs
    lateinit var interactiveFlowSessionManager: InteractiveFlowSessionManager

    @Test
    fun `verifyEncodedInternalState - Return failure when state is null`() = runTest {
        val result = interactiveFlowSessionManager.verifyEncodedInternalState(null)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.missing_state", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when state is blank`() = runTest {
        val result = interactiveFlowSessionManager.verifyEncodedInternalState("   ")

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.missing_state", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when JWT signature is invalid`() = runTest {
        val state = "invalid.jwt.token"
        coEvery {
            jwtManager.decodeAndVerifyOrNull(InteractiveFlowSessionManager.STATE_KEY_NAME, state)
        } returns null

        val result = interactiveFlowSessionManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.wrong_signature", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when JWT subject is not a valid UUID`() = runTest {
        val state = "valid.jwt.token"
        val jwt = DecodedJwt(id = null, subject = "not-a-uuid", keyId = null)
        coEvery {
            jwtManager.decodeAndVerifyOrNull(InteractiveFlowSessionManager.STATE_KEY_NAME, state)
        } returns jwt

        val result = interactiveFlowSessionManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.invalid_subject", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when session is not found`() = runTest {
        val state = "valid.jwt.token"
        val sessionId = UUID.randomUUID()
        val jwt = DecodedJwt(id = null, subject = sessionId.toString(), keyId = null)
        coEvery {
            jwtManager.decodeAndVerifyOrNull(InteractiveFlowSessionManager.STATE_KEY_NAME, state)
        } returns jwt
        coEvery { sessionRepository.findById(sessionId) } returns null

        val result = interactiveFlowSessionManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.missing_session", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return success when state is valid`() = runTest {
        val state = "valid.jwt.token"
        val sessionId = UUID.randomUUID()
        val jwt = DecodedJwt(id = null, subject = sessionId.toString(), keyId = null)
        val entity = mockk<InteractiveFlowSessionEntity>()
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery {
            jwtManager.decodeAndVerifyOrNull(InteractiveFlowSessionManager.STATE_KEY_NAME, state)
        } returns jwt
        coEvery { sessionRepository.findById(sessionId) } returns entity
        every { sessionMapper.toInteractiveFlowSession(entity) } returns session

        val result = interactiveFlowSessionManager.verifyEncodedInternalState(state)

        assertTrue(result is SuccessVerifyEncodedStateResult)
        result as SuccessVerifyEncodedStateResult
        assertSame(session, result.session)
    }

    @Test
    fun `insertPurposeAfter - Inserts the purpose right after the given one and persists the list`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = OnGoingInteractiveFlowSession(
            id = sessionId,
            purposes = listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            ),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = UUID.randomUUID(),
        )
        // Stub with the exact expected persisted names so reaching the assertion proves the right ordering was
        // saved: the inserted MFA_CHALLENGE lands right after REAUTHENTICATION, ahead of the trailing purpose.
        coEvery {
            sessionRepository.updatePurposes(
                sessionId,
                listOf(
                    InteractiveFlowPurpose.CONFIRM.name,
                    InteractiveFlowPurpose.REAUTHENTICATION.name,
                    InteractiveFlowPurpose.MFA_CHALLENGE.name,
                    InteractiveFlowPurpose.MFA_ENROLLMENT.name,
                )
            )
        } returns Unit

        val result = interactiveFlowSessionManager.insertPurposeAfter(
            session,
            purpose = InteractiveFlowPurpose.MFA_CHALLENGE,
            afterPurpose = InteractiveFlowPurpose.REAUTHENTICATION,
        )

        assertEquals(
            listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.MFA_CHALLENGE,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            ),
            result.purposes
        )
    }

    @Test
    fun `markPurposeAsCompleted - Records the purpose and stays ongoing without completing the session`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = OnGoingInteractiveFlowSession(
            id = sessionId,
            purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = UUID.randomUUID(),
        )
        // Stub with the exact expected persisted names so reaching the assertion proves the right list was saved.
        coEvery {
            sessionRepository.updateCompletedPurposes(sessionId, listOf(InteractiveFlowPurpose.CONFIRM.name))
        } returns Unit

        val result = interactiveFlowSessionManager.markPurposeAsCompleted(
            session, InteractiveFlowPurpose.CONFIRM
        )

        assertEquals(listOf(InteractiveFlowPurpose.CONFIRM), result.completedPurposes)
        // Bookkeeping only: the session must not be completed even though a purpose was recorded.
        coVerify(exactly = 0) { sessionRepository.updateCompleteDate(any(), any()) }
    }

    @Test
    fun `markPurposeAsCompleted - Recording an already-completed purpose is a no-op on the list`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = OnGoingInteractiveFlowSession(
            id = sessionId,
            purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
            completedPurposes = listOf(InteractiveFlowPurpose.CONFIRM),
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = UUID.randomUUID(),
        )
        coEvery {
            sessionRepository.updateCompletedPurposes(sessionId, listOf(InteractiveFlowPurpose.CONFIRM.name))
        } returns Unit

        val result = interactiveFlowSessionManager.markPurposeAsCompleted(
            session, InteractiveFlowPurpose.CONFIRM
        )

        assertEquals(listOf(InteractiveFlowPurpose.CONFIRM), result.completedPurposes)
    }

    @Test
    fun `markAsCompleted - Persists the complete date and returns a completed session`() = runTest {
        val sessionId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val redirectUri = URI.create("https://client.example.com/callback")
        val session = OnGoingInteractiveFlowSession(
            id = sessionId,
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            completedPurposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE),
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = userId,
            successRedirectUri = redirectUri,
            redirectType = InteractiveFlowRedirectType.AUTHORIZATION_CODE,
            cancelRedirectUri = redirectUri,
        )
        coEvery { sessionRepository.updateCompleteDate(sessionId, any()) } returns Unit

        val result = interactiveFlowSessionManager.markAsCompleted(session)

        assertEquals(userId, result.userId)
        assertEquals(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE),
            result.completedPurposes
        )
        assertEquals(redirectUri, result.successRedirectUri)
        assertEquals(InteractiveFlowRedirectType.AUTHORIZATION_CODE, result.redirectType)
    }

    @Test
    fun `markAsCancelled - Persists the cancel date and returns a cancelled session`() = runTest {
        val sessionId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val redirectUri = URI.create("https://client.example.com/callback")
        val session = OnGoingInteractiveFlowSession(
            id = sessionId,
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = userId,
            successRedirectUri = redirectUri,
            redirectType = InteractiveFlowRedirectType.AUTHORIZATION_CODE,
            cancelRedirectUri = redirectUri,
        )
        coEvery { sessionRepository.updateCancelDate(sessionId, any()) } returns Unit

        val result = interactiveFlowSessionManager.markAsCancelled(session)

        assertEquals(sessionId, result.id)
        assertEquals(userId, result.userId)
        assertEquals(InteractiveFlowRedirectType.AUTHORIZATION_CODE, result.redirectType)
        assertEquals(redirectUri, result.cancelRedirectUri)
    }

    @Test
    fun `markAsCancelled - Throws a recoverable error for a PLAIN session with no cancel target`() = runTest {
        val session = OnGoingInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.MFA_ENROLLMENT),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = UUID.randomUUID(),
            successRedirectUri = URI.create("https://client.example.com/enrolled"),
            redirectType = InteractiveFlowRedirectType.PLAIN,
            cancelRedirectUri = null,
        )

        val exception = assertThrows<com.sympauthy.business.exception.BusinessException> {
            interactiveFlowSessionManager.markAsCancelled(session)
        }
        assertEquals("auth.interactive_flow_session.cancel.no_cancel_target", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { sessionRepository.updateCancelDate(any(), any()) }
    }
}

package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMapper
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
}

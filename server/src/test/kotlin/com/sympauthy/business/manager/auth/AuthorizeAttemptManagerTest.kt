package com.sympauthy.business.manager.auth

import com.auth0.jwt.interfaces.DecodedJWT
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.AuthorizeAttemptMapper
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.data.model.AuthorizeAttemptEntity
import com.sympauthy.data.repository.AuthorizeAttemptRepository
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
class AuthorizeAttemptManagerTest {

    @MockK
    lateinit var authorizeAttemptRepository: AuthorizeAttemptRepository

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var authorizeAttemptMapper: AuthorizeAttemptMapper

    @SpyK
    @InjectMockKs
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @Test
    fun `verifyEncodedInternalState - Return failure when state is null`() = runTest {
        val result = authorizeAttemptManager.verifyEncodedInternalState(null)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.missing", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when state is blank`() = runTest {
        val result = authorizeAttemptManager.verifyEncodedInternalState("   ")

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.missing", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when JWT signature is invalid`() = runTest {
        val state = "invalid.jwt.token"
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns null

        val result = authorizeAttemptManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.wrong_signature", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when JWT subject is not a valid UUID`() = runTest {
        val state = "valid.jwt.token"
        val jwt = mockk<DecodedJWT> {
            every { subject } returns "not-a-uuid"
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt

        val result = authorizeAttemptManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.invalid_subject", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when authorize attempt is not found`() = runTest {
        val state = "valid.jwt.token"
        val attemptId = UUID.randomUUID()
        val jwt = mockk<DecodedJWT> {
            every { subject } returns attemptId.toString()
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt
        coEvery { authorizeAttemptRepository.findById(attemptId) } returns null

        val result = authorizeAttemptManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.expired", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when authorize attempt is expired`() = runTest {
        val state = "valid.jwt.token"
        val attemptId = UUID.randomUUID()
        val jwt = mockk<DecodedJWT> {
            every { subject } returns attemptId.toString()
        }
        val entity = mockk<AuthorizeAttemptEntity>()
        val authorizeAttempt = mockk<AuthorizeAttempt> {
            every { expired } returns true
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt
        coEvery { authorizeAttemptRepository.findById(attemptId) } returns entity
        every { authorizeAttemptMapper.toAuthorizeAttempt(entity) } returns authorizeAttempt

        val result = authorizeAttemptManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.expired", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when authorize attempt has error`() = runTest {
        val state = "valid.jwt.token"
        val attemptId = UUID.randomUUID()
        val jwt = mockk<DecodedJWT> {
            every { subject } returns attemptId.toString()
        }
        val entity = mockk<AuthorizeAttemptEntity>()
        val errorDetailsId = "error.details"
        val authorizeAttempt = mockk<FailedAuthorizeAttempt> {
            val mock = this
            every { mock.expired } returns false
            every { mock.errorDetailsId } returns errorDetailsId
            every { mock.errorDescriptionId } returns null
            every { mock.errorValues } returns null
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt
        coEvery { authorizeAttemptRepository.findById(attemptId) } returns entity
        every { authorizeAttemptMapper.toAuthorizeAttempt(entity) } returns authorizeAttempt

        val result = authorizeAttemptManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals(errorDetailsId, result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return success when state is valid`() = runTest {
        val state = "valid.jwt.token"
        val attemptId = UUID.randomUUID()
        val jwt = mockk<DecodedJWT> {
            every { subject } returns attemptId.toString()
        }
        val entity = mockk<AuthorizeAttemptEntity>()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            every { expired } returns false
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt
        coEvery { authorizeAttemptRepository.findById(attemptId) } returns entity
        every { authorizeAttemptMapper.toAuthorizeAttempt(entity) } returns authorizeAttempt

        val result = authorizeAttemptManager.verifyEncodedInternalState(state)

        assertTrue(result is SuccessVerifyEncodedStateResult)
        result as SuccessVerifyEncodedStateResult
        assertSame(authorizeAttempt, result.authorizeAttempt)
    }
}

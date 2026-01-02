package com.sympauthy.business.manager.auth

import com.auth0.jwt.interfaces.DecodedJWT
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.AuthorizeAttemptMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
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
    fun `getAllowedScopesForClient - Return provided scopes when client has not allowed scopes`() {
        val scope = mockk<Scope>()
        val client = mockk<Client> {
            every { allowedScopes } returns null
        }

        val result = authorizeAttemptManager.getAllowedScopesForClient(client, listOf(scope))

        assertEquals(1, result.count())
        assertSame(scope, result.getOrNull(0))
    }

    @Test
    fun `getAllowedScopesForClient - Return default scopes when no scope are provided`() {
        val scope = mockk<Scope>()
        val client = mockk<Client> {
            every { defaultScopes } returns listOf(scope)
        }

        val result = authorizeAttemptManager.getAllowedScopesForClient(client, null)

        assertEquals(1, result.count())
        assertSame(scope, result.getOrNull(0))
    }

    @Test
    fun `getAllowedScopesForClient - Filter according to allowed scopes`() {
        val allowedScopeOne = "allowedScopeOne"
        val allowedScope = mockk<Scope> {
            every { scope } returns allowedScopeOne
        }
        val uncheckedScopeOne = mockk<Scope> {
            every { scope } returns allowedScopeOne
        }
        val uncheckedScopeTwo = mockk<Scope> {
            every { scope } returns "notAllowedScopeOne"
        }
        val client = mockk<Client> {
            every { allowedScopes } returns setOf(allowedScope)
        }

        val result = authorizeAttemptManager.getAllowedScopesForClient(
            client = client,
            uncheckedScopes = listOf(uncheckedScopeOne, uncheckedScopeTwo)
        )

        assertEquals(1, result.count())
        assertSame(uncheckedScopeOne, result.getOrNull(0))
    }

    @Test
    fun `verifyEncodedState - Return failure when state is null`() = runTest {
        val result = authorizeAttemptManager.verifyEncodedState(null)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.missing", result.detailsId)
    }

    @Test
    fun `verifyEncodedState - Return failure when state is blank`() = runTest {
        val result = authorizeAttemptManager.verifyEncodedState("   ")

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.missing", result.detailsId)
    }

    @Test
    fun `verifyEncodedState - Return failure when JWT signature is invalid`() = runTest {
        val state = "invalid.jwt.token"
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns null

        val result = authorizeAttemptManager.verifyEncodedState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.wrong_signature", result.detailsId)
    }

    @Test
    fun `verifyEncodedState - Return failure when JWT subject is not a valid UUID`() = runTest {
        val state = "valid.jwt.token"
        val jwt = mockk<DecodedJWT> {
            every { subject } returns "not-a-uuid"
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt

        val result = authorizeAttemptManager.verifyEncodedState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.invalid_subject", result.detailsId)
    }

    @Test
    fun `verifyEncodedState - Return failure when authorize attempt is not found`() = runTest {
        val state = "valid.jwt.token"
        val attemptId = UUID.randomUUID()
        val jwt = mockk<DecodedJWT> {
            every { subject } returns attemptId.toString()
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt
        coEvery { authorizeAttemptRepository.findById(attemptId) } returns null

        val result = authorizeAttemptManager.verifyEncodedState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.expired", result.detailsId)
    }

    @Test
    fun `verifyEncodedState - Return failure when authorize attempt is expired`() = runTest {
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

        val result = authorizeAttemptManager.verifyEncodedState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.authorize_attempt.validate.expired", result.detailsId)
    }

    @Test
    fun `verifyEncodedState - Return failure when authorize attempt has error`() = runTest {
        val state = "valid.jwt.token"
        val attemptId = UUID.randomUUID()
        val jwt = mockk<DecodedJWT> {
            every { subject } returns attemptId.toString()
        }
        val entity = mockk<AuthorizeAttemptEntity>()
        val errorDetailsId = "error.details"
        val authorizeAttempt = mockk<AuthorizeAttempt> {
            val mock = this
            every { mock.expired } returns false
            every { mock.errorDetailsId } returns errorDetailsId
            every { mock.errorDescriptionId } returns null
            every { mock.errorValues } returns null
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt
        coEvery { authorizeAttemptRepository.findById(attemptId) } returns entity
        every { authorizeAttemptMapper.toAuthorizeAttempt(entity) } returns authorizeAttempt

        val result = authorizeAttemptManager.verifyEncodedState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals(errorDetailsId, result.detailsId)
    }

    @Test
    fun `verifyEncodedState - Return success when state is valid`() = runTest {
        val state = "valid.jwt.token"
        val attemptId = UUID.randomUUID()
        val jwt = mockk<DecodedJWT> {
            every { subject } returns attemptId.toString()
        }
        val entity = mockk<AuthorizeAttemptEntity>()
        val authorizeAttempt = mockk<AuthorizeAttempt> {
            every { expired } returns false
            every { errorDetailsId } returns null
        }
        coEvery { jwtManager.decodeAndVerifyOrNull(AuthorizeAttemptManager.STATE_KEY_NAME, state) } returns jwt
        coEvery { authorizeAttemptRepository.findById(attemptId) } returns entity
        every { authorizeAttemptMapper.toAuthorizeAttempt(entity) } returns authorizeAttempt

        val result = authorizeAttemptManager.verifyEncodedState(state)

        assertTrue(result is SuccessVerifyEncodedStateResult)
        result as SuccessVerifyEncodedStateResult
        assertSame(authorizeAttempt, result.authorizeAttempt)
    }
}

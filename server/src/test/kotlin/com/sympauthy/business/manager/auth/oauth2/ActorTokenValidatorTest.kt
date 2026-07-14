package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.mapper.AuthenticationTokenMapper
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
@MockKExtension.CheckUnnecessaryStub
class ActorTokenValidatorTest {

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @MockK
    lateinit var tokenMapper: AuthenticationTokenMapper

    @InjectMockKs
    lateinit var actorTokenValidator: ActorTokenValidator

    @Test
    fun `validateActorToken - No-op when token is not an act-as token`() = runTest {
        val token = mockk<AuthenticationToken> {
            every { actorTokenId } returns null
        }

        assertDoesNotThrow {
            actorTokenValidator.validateActorToken(token)
        }
        coVerify(exactly = 0) { tokenRepository.findById(any()) }
    }

    @Test
    fun `validateActorToken - Throws when actor token is missing`() = runTest {
        val actorTokenId = UUID.randomUUID()
        val token = mockk<AuthenticationToken> {
            every { this@mockk.actorTokenId } returns actorTokenId
        }

        coEvery { tokenRepository.findById(actorTokenId) } returns null

        val exception = assertThrows<OAuth2Exception> {
            actorTokenValidator.validateActorToken(token)
        }
        assertEquals("token.actor_invalid", exception.detailsId)
    }

    @Test
    fun `validateActorToken - Throws when actor token is revoked`() = runTest {
        val actorTokenId = UUID.randomUUID()
        val token = mockk<AuthenticationToken> {
            every { this@mockk.actorTokenId } returns actorTokenId
        }
        val actorEntity = mockk<AuthenticationTokenEntity>()
        val actorToken = mockk<AuthenticationToken> {
            every { revoked } returns true
        }

        coEvery { tokenRepository.findById(actorTokenId) } returns actorEntity
        every { tokenMapper.toToken(actorEntity) } returns actorToken

        val exception = assertThrows<OAuth2Exception> {
            actorTokenValidator.validateActorToken(token)
        }
        assertEquals("token.actor_revoked", exception.detailsId)
    }

    @Test
    fun `validateActorToken - Throws when actor token is expired`() = runTest {
        val actorTokenId = UUID.randomUUID()
        val token = mockk<AuthenticationToken> {
            every { this@mockk.actorTokenId } returns actorTokenId
        }
        val actorEntity = mockk<AuthenticationTokenEntity>()
        val actorToken = mockk<AuthenticationToken> {
            every { revoked } returns false
            every { expired } returns true
        }

        coEvery { tokenRepository.findById(actorTokenId) } returns actorEntity
        every { tokenMapper.toToken(actorEntity) } returns actorToken

        val exception = assertThrows<OAuth2Exception> {
            actorTokenValidator.validateActorToken(token)
        }
        assertEquals("token.actor_expired", exception.detailsId)
    }

    @Test
    fun `validateActorToken - Passes when actor token is active`() = runTest {
        val actorTokenId = UUID.randomUUID()
        val token = mockk<AuthenticationToken> {
            every { this@mockk.actorTokenId } returns actorTokenId
        }
        val actorEntity = mockk<AuthenticationTokenEntity>()
        val actorToken = mockk<AuthenticationToken> {
            every { revoked } returns false
            every { expired } returns false
        }

        coEvery { tokenRepository.findById(actorTokenId) } returns actorEntity
        every { tokenMapper.toToken(actorEntity) } returns actorToken

        assertDoesNotThrow {
            actorTokenValidator.validateActorToken(token)
        }
    }
}

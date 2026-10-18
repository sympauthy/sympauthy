package com.sympauthy.business.manager.auth

import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.model.grant.Grant
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
class ActorGrantValidatorTest {

    @MockK
    lateinit var grantManager: GrantManager

    @MockK
    lateinit var tokenRepository: AuthenticationTokenRepository

    @InjectMockKs
    lateinit var actorGrantValidator: ActorGrantValidator

    private fun mockActAsGrant(actorGrantId: UUID): Grant = mockk {
        every { this@mockk.actorGrantId } returns actorGrantId
    }

    @Test
    fun `validateActorGrant - No-op when the grant is not an act-as grant`() = runTest {
        val grant = mockk<Grant> {
            every { actorGrantId } returns null
        }

        assertDoesNotThrow {
            actorGrantValidator.validateActorGrant(grant)
        }
        coVerify(exactly = 0) { grantManager.findByIdOrNull(any()) }
    }

    @Test
    fun `validateActorGrant - Answers an actor grant the cleaner collected as expired`() = runTest {
        val actorGrantId = UUID.randomUUID()
        coEvery { grantManager.findByIdOrNull(actorGrantId) } returns null

        val exception = assertThrows<OAuth2Exception> {
            actorGrantValidator.validateActorGrant(mockActAsGrant(actorGrantId))
        }
        assertEquals("grant.actor_expired", exception.detailsId)
    }

    @Test
    fun `validateActorGrant - Throws when the actor grant is past its lifetime`() = runTest {
        val actorGrantId = UUID.randomUUID()
        coEvery { grantManager.findByIdOrNull(actorGrantId) } returns mockk {
            every { revoked } returns false
            every { expired } returns true
        }

        val exception = assertThrows<OAuth2Exception> {
            actorGrantValidator.validateActorGrant(mockActAsGrant(actorGrantId))
        }
        assertEquals("grant.actor_expired", exception.detailsId)
        coVerify(exactly = 0) { tokenRepository.countLiveByGrantId(any(), any()) }
    }

    @Test
    fun `validateActorGrant - Throws when the actor grant is revoked`() = runTest {
        val actorGrantId = UUID.randomUUID()
        coEvery { grantManager.findByIdOrNull(actorGrantId) } returns mockk {
            every { revoked } returns true
        }

        val exception = assertThrows<OAuth2Exception> {
            actorGrantValidator.validateActorGrant(mockActAsGrant(actorGrantId))
        }
        assertEquals("grant.actor_revoked", exception.detailsId)
        coVerify(exactly = 0) { tokenRepository.countLiveByGrantId(any(), any()) }
    }

    @Test
    fun `validateActorGrant - Throws when every token of the actor grant has expired`() = runTest {
        val actorGrantId = UUID.randomUUID()
        coEvery { grantManager.findByIdOrNull(actorGrantId) } returns mockk {
            every { revoked } returns false
            every { expired } returns false
        }
        coEvery { tokenRepository.countLiveByGrantId(actorGrantId, any()) } returns 0

        val exception = assertThrows<OAuth2Exception> {
            actorGrantValidator.validateActorGrant(mockActAsGrant(actorGrantId))
        }
        assertEquals("grant.actor_expired", exception.detailsId)
    }

    @Test
    fun `validateActorGrant - Passes when the actor grant is live`() = runTest {
        val actorGrantId = UUID.randomUUID()
        coEvery { grantManager.findByIdOrNull(actorGrantId) } returns mockk {
            every { revoked } returns false
            every { expired } returns false
        }
        coEvery { tokenRepository.countLiveByGrantId(actorGrantId, any()) } returns 1

        assertDoesNotThrow {
            actorGrantValidator.validateActorGrant(mockActAsGrant(actorGrantId))
        }
    }
}

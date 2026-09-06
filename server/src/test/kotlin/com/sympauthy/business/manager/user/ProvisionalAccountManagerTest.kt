package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.AuthenticationTokenRepository
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.ConsentRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.TotpEnrollmentRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.data.repository.ValidationCodeRepository
import com.sympauthy.business.model.user.claim.Claim
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ProvisionalAccountManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var passwordRepository: PasswordRepository

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var providerUserInfoRepository: ProviderUserInfoRepository

    @MockK
    lateinit var totpEnrollmentRepository: TotpEnrollmentRepository

    @MockK
    lateinit var validationCodeRepository: ValidationCodeRepository

    @MockK
    lateinit var consentRepository: ConsentRepository

    @MockK
    lateinit var authenticationTokenRepository: AuthenticationTokenRepository

    @InjectMockKs
    lateinit var manager: ProvisionalAccountManager

    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `promote - Clears the session of every table the account owns`() = runTest {
        provisionalUser()
        noIdentifierClaim()
        coEvery { providerUserInfoRepository.findByUserId(userId) } returns emptyList()
        coEvery { passwordRepository.clearSessionId(userId, sessionId) } returns 1
        coEvery { collectedClaimRepository.clearSessionId(userId, sessionId) } returns 2
        coEvery { providerUserInfoRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { totpEnrollmentRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { userRepository.clearSessionId(userId, sessionId) } returns 1

        manager.promote(sessionId, userId)

        coVerifyOrder {
            passwordRepository.clearSessionId(userId, sessionId)
            collectedClaimRepository.clearSessionId(userId, sessionId)
            providerUserInfoRepository.clearSessionId(userId, sessionId)
            totpEnrollmentRepository.clearSessionId(userId, sessionId)
            userRepository.clearSessionId(userId, sessionId)
        }
    }

    @Test
    fun `promote - Does nothing when the session signed no account up`() = runTest {
        coEvery { userRepository.findByIdAndSessionId(userId, sessionId) } returns null

        manager.promote(sessionId, userId)

        coVerify(exactly = 0) { userRepository.clearSessionId(any(), any()) }
        coVerify(exactly = 0) { collectedClaimRepository.clearSessionId(any(), any()) }
    }

    @Test
    fun `promote - Refuses when a committed account has taken an identifier claim`() = runTest {
        provisionalUser()
        identifierClaims("email")
        coEvery {
            collectedClaimRepository.findByUserIdAndClaimInList(userId, listOf("email"))
        } returns listOf(claimEntity("email", "\"taken@example.com\""))
        coEvery {
            userManager.isIdentifierValueTaken(listOf("email"), listOf("\"taken@example.com\""))
        } returns true

        val exception = assertThrows<BusinessException> { manager.promote(sessionId, userId) }

        assertEquals("user.promote.identifier_taken", exception.detailsId)
        coVerify(exactly = 0) { userRepository.clearSessionId(any(), any()) }
    }

    @Test
    fun `promote - Refuses when a committed account has taken a provider identity`() = runTest {
        provisionalUser()
        noIdentifierClaim()
        coEvery { providerUserInfoRepository.findByUserId(userId) } returns listOf(link("discord", "subject-1"))
        coEvery {
            providerUserInfoRepository.findByProviderIdAndSubjectAndSessionIdIsNull("discord", "subject-1")
        } returns link("discord", "subject-1")

        val exception = assertThrows<BusinessException> { manager.promote(sessionId, userId) }

        assertEquals("user.promote.provider_subject_taken", exception.detailsId)
        assertEquals("discord", exception.values["providerId"])
        coVerify(exactly = 0) { userRepository.clearSessionId(any(), any()) }
    }

    @Test
    fun `promote - Proceeds when no committed account holds the provider identity`() = runTest {
        provisionalUser()
        noIdentifierClaim()
        coEvery { providerUserInfoRepository.findByUserId(userId) } returns listOf(link("discord", "subject-1"))
        coEvery {
            providerUserInfoRepository.findByProviderIdAndSubjectAndSessionIdIsNull("discord", "subject-1")
        } returns null
        coEvery { passwordRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { collectedClaimRepository.clearSessionId(userId, sessionId) } returns 1
        coEvery { providerUserInfoRepository.clearSessionId(userId, sessionId) } returns 1
        coEvery { totpEnrollmentRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { userRepository.clearSessionId(userId, sessionId) } returns 1

        manager.promote(sessionId, userId)

        coVerify { userRepository.clearSessionId(userId, sessionId) }
    }

    @Test
    fun `deleteAbandoned - Removes every row the account owns before the account`() = runTest {
        val abandonedId = UUID.randomUUID()
        abandoned(collectable = listOf(abandonedId))
        coEvery { passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 1
        coEvery { collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 2
        coEvery { providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 0
        coEvery { totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 0
        coEvery { validationCodeRepository.deleteByUserIdInAndUserProvisional(listOf(abandonedId)) } returns 0
        coEvery { consentRepository.deleteByUserIdInAndUserProvisional(listOf(abandonedId)) } returns 0
        coEvery { authenticationTokenRepository.deleteByUserIdInAndUserProvisional(listOf(abandonedId)) } returns 0
        coEvery { userRepository.deleteByIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 1

        val result = manager.deleteAbandoned(BATCH_SIZE)

        assertEquals(1, result.deletedCount)
        assertEquals(emptyList<UUID>(), result.retainedIds)
        coVerifyOrder {
            passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId))
            collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId))
            providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId))
            totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId))
            validationCodeRepository.deleteByUserIdInAndUserProvisional(listOf(abandonedId))
            consentRepository.deleteByUserIdInAndUserProvisional(listOf(abandonedId))
            authenticationTokenRepository.deleteByUserIdInAndUserProvisional(listOf(abandonedId))
            userRepository.deleteByIdInAndSessionIdIsNotNull(listOf(abandonedId))
        }
    }

    @Test
    fun `deleteAbandoned - Touches no table when nothing was abandoned`() = runTest {
        abandoned(collectable = emptyList())

        val result = manager.deleteAbandoned(BATCH_SIZE)

        assertEquals(0, result.deletedCount)
        assertEquals(emptyList<UUID>(), result.retainedIds)
        coVerify(exactly = 0) { passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) }
        coVerify(exactly = 0) { consentRepository.deleteByUserIdInAndUserProvisional(any()) }
        coVerify(exactly = 0) { userRepository.deleteByIdInAndSessionIdIsNotNull(any()) }
    }

    @Test
    fun `deleteAbandoned - Names the account a row still refers to and deletes the other`() = runTest {
        val collectableId = UUID.randomUUID()
        val retainedId = UUID.randomUUID()
        abandoned(collectable = listOf(collectableId), retained = listOf(retainedId))
        coEvery { passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(collectableId)) } returns 0
        coEvery { collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(collectableId)) } returns 0
        coEvery { providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(collectableId)) } returns 0
        coEvery { totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(collectableId)) } returns 0
        coEvery { validationCodeRepository.deleteByUserIdInAndUserProvisional(listOf(collectableId)) } returns 0
        coEvery { consentRepository.deleteByUserIdInAndUserProvisional(listOf(collectableId)) } returns 0
        coEvery {
            authenticationTokenRepository.deleteByUserIdInAndUserProvisional(listOf(collectableId))
        } returns 0
        coEvery { userRepository.deleteByIdInAndSessionIdIsNotNull(listOf(collectableId)) } returns 1

        val result = manager.deleteAbandoned(BATCH_SIZE)

        assertEquals(1, result.deletedCount)
        assertEquals(listOf(retainedId), result.retainedIds)
    }

    @Test
    fun `deleteAbandoned - Deletes nothing when every abandoned account is retained`() = runTest {
        val retainedId = UUID.randomUUID()
        abandoned(collectable = emptyList(), retained = listOf(retainedId))

        val result = manager.deleteAbandoned(BATCH_SIZE)

        assertEquals(0, result.deletedCount)
        assertEquals(listOf(retainedId), result.retainedIds)
        coVerify(exactly = 0) { passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) }
        coVerify(exactly = 0) { userRepository.deleteByIdInAndSessionIdIsNotNull(any()) }
    }

    @Test
    fun `deleteAbandoned - Says the batch filled when it took as many collectable as it was allowed`() =
        runTest {
            val collectableId = UUID.randomUUID()
            abandoned(collectable = listOf(collectableId), limit = 1)
            coEvery { passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) } returns 0
            coEvery { collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) } returns 0
            coEvery { providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) } returns 0
            coEvery { totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) } returns 0
            coEvery { validationCodeRepository.deleteByUserIdInAndUserProvisional(any()) } returns 0
            coEvery { consentRepository.deleteByUserIdInAndUserProvisional(any()) } returns 0
            coEvery { authenticationTokenRepository.deleteByUserIdInAndUserProvisional(any()) } returns 0
            coEvery { userRepository.deleteByIdInAndSessionIdIsNotNull(any()) } returns 1

            assertTrue(manager.deleteAbandoned(1).filledBatch)
        }

    /**
     * A retained account is retained for good, so one counted against the limit would be counted again on
     * every run after this one — and enough of them would leave the sweep no budget to collect anything.
     */
    @Test
    fun `deleteAbandoned - Does not spend the batch on the accounts it retains`() = runTest {
        val collectableId = UUID.randomUUID()
        abandoned(collectable = listOf(collectableId), retained = List(5) { UUID.randomUUID() }, limit = 1)
        coEvery { passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) } returns 0
        coEvery { collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) } returns 0
        coEvery { providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) } returns 0
        coEvery { totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) } returns 0
        coEvery { validationCodeRepository.deleteByUserIdInAndUserProvisional(any()) } returns 0
        coEvery { consentRepository.deleteByUserIdInAndUserProvisional(any()) } returns 0
        coEvery { authenticationTokenRepository.deleteByUserIdInAndUserProvisional(any()) } returns 0
        coEvery { userRepository.deleteByIdInAndSessionIdIsNotNull(listOf(collectableId)) } returns 1

        val result = manager.deleteAbandoned(1)

        assertEquals(1, result.deletedCount)
        assertEquals(5, result.retainedIds.size)
    }

    private fun abandonedUser(id: UUID) = UserEntity(
        status = "ENABLED",
        creationDate = LocalDateTime.now(),
        sessionId = UUID.randomUUID()
    ).apply { this.id = id }

    private fun abandoned(collectable: List<UUID>, retained: List<UUID> = emptyList(), limit: Int = BATCH_SIZE) {
        coEvery { userRepository.findCollectable(limit) } returns collectable.map(::abandonedUser)
        coEvery { userRepository.findRetained(limit) } returns retained.map(::abandonedUser)
    }

    private fun provisionalUser() {
        coEvery { userRepository.findByIdAndSessionId(userId, sessionId) } returns UserEntity(
            status = "ENABLED",
            creationDate = LocalDateTime.now(),
            sessionId = sessionId
        )
    }

    private fun noIdentifierClaim() {
        every { claimManager.listIdentifierClaims() } returns emptyList()
    }

    @Test
    fun `promote - Proceeds when the identifier the account holds is still free`() = runTest {
        provisionalUser()
        identifierClaims("email")
        coEvery {
            collectedClaimRepository.findByUserIdAndClaimInList(userId, listOf("email"))
        } returns listOf(claimEntity("email", "\"free@example.com\""))
        coEvery {
            userManager.isIdentifierValueTaken(listOf("email"), listOf("\"free@example.com\""))
        } returns false
        coEvery { providerUserInfoRepository.findByUserId(userId) } returns emptyList()
        coEvery { passwordRepository.clearSessionId(userId, sessionId) } returns 1
        coEvery { collectedClaimRepository.clearSessionId(userId, sessionId) } returns 1
        coEvery { providerUserInfoRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { totpEnrollmentRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { userRepository.clearSessionId(userId, sessionId) } returns 1

        manager.promote(sessionId, userId)

        coVerify { userRepository.clearSessionId(userId, sessionId) }
    }

    private fun identifierClaims(vararg ids: String) {
        every { claimManager.listIdentifierClaims() } returns ids.map { id ->
            mockk<Claim> { every { this@mockk.id } returns id }
        }
    }

    private fun claimEntity(claim: String, value: String) = CollectedClaimEntity(
        userId = userId,
        claim = claim,
        value = value,
        verified = null,
        collectionDate = LocalDateTime.now(),
        verificationDate = null,
        sessionId = sessionId
    )

    private fun link(providerId: String, subject: String) = ProviderUserInfoEntity(
        id = ProviderUserInfoEntityId(providerId = providerId, userId = userId),
        linkDate = LocalDateTime.now(),
        fetchDate = LocalDateTime.now(),
        changeDate = LocalDateTime.now(),
        subject = subject,
        sessionId = sessionId
    )

    companion object {
        /** A bound the sweeps under test never come near, where the bound is not what they prove. */
        private const val BATCH_SIZE = 100
    }
}

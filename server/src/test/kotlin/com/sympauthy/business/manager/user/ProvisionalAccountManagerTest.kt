package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.ObjectLockRepository
import com.sympauthy.data.repository.PasswordRepository
import com.sympauthy.data.repository.ProviderUserInfoRepository
import com.sympauthy.data.repository.TotpEnrollmentRepository
import com.sympauthy.data.repository.UserRepository
import com.sympauthy.business.model.user.claim.Claim
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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

    /**
     * The lock manager is the real one over a mocked stripe table, rather than a double answering
     * whatever it is asked: what these tests have to see is which rows a promotion takes and when, and a
     * stubbed `withLock` would run the block and prove neither.
     */
    @MockK(relaxed = true)
    lateinit var objectLockRepository: ObjectLockRepository

    @MockK
    lateinit var claimValueMapper: ClaimValueMapper

    lateinit var manager: ProvisionalAccountManager

    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    /** The account a refusal names as holding the value, which reaches an operator and never the person refused. */
    private val ownerId = UUID.randomUUID()


    @BeforeEach
    fun setUp() {
        manager = ProvisionalAccountManager(
            claimManager = claimManager,
            claimValueMapper = claimValueMapper,
            userManager = userManager,
            lockManager = LockManager(objectLockRepository, HeldStripes()),
            userRepository = userRepository,
            passwordRepository = passwordRepository,
            collectedClaimRepository = collectedClaimRepository,
            providerUserInfoRepository = providerUserInfoRepository,
            totpEnrollmentRepository = totpEnrollmentRepository
        )
    }

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
        } returns listOf(claimEntity("email", "taken@example.com"))
        stubFoldOf("email", "taken@example.com")
        coEvery {
            userManager.findTakenIdentifierOrNull(
                userId, listOf("email"), mapOf("email" to "taken@example.com")
            )
        } returns TakenIdentifier(claimId = "email", userId = ownerId)
        coEvery { providerUserInfoRepository.findByUserId(userId) } returns emptyList()

        val exception = assertThrows<BusinessException> { manager.promote(sessionId, userId) }

        assertEquals("user.promote.identifier_taken", exception.detailsId)
        assertEquals("email", exception.values["claim"])
        assertEquals(ownerId.toString(), exception.values["userId"])
        coVerify(exactly = 0) { userRepository.clearSessionId(any(), any()) }
        // The key names the folded value, which is what the check it protects compares on. Which row a
        // key maps to is LockKeyTest's; that it is this key is this test's.
        coVerifyOrder {
            objectLockRepository.lock(LockKey.IdentifierValue("taken@example.com").stripe)
            userManager.findTakenIdentifierOrNull(
                userId, listOf("email"), mapOf("email" to "taken@example.com")
            )
        }
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
        coVerifyOrder {
            objectLockRepository.lock(LockKey.ProviderSubject("discord", "subject-1").stripe)
            providerUserInfoRepository.findByProviderIdAndSubjectAndSessionIdIsNull("discord", "subject-1")
        }
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
        coEvery { userRepository.findAbandoned() } returns listOf(abandonedUser(abandonedId))
        coEvery { passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 1
        coEvery { collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 2
        coEvery { providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 0
        coEvery { totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 0
        coEvery { userRepository.deleteByIdInAndSessionIdIsNotNull(listOf(abandonedId)) } returns 1

        assertEquals(1, manager.deleteAbandoned())

        coVerifyOrder {
            passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId))
            collectedClaimRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId))
            providerUserInfoRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId))
            totpEnrollmentRepository.deleteByUserIdInAndSessionIdIsNotNull(listOf(abandonedId))
            userRepository.deleteByIdInAndSessionIdIsNotNull(listOf(abandonedId))
        }
    }

    @Test
    fun `deleteAbandoned - Touches no table when nothing was abandoned`() = runTest {
        coEvery { userRepository.findAbandoned() } returns emptyList()

        assertEquals(0, manager.deleteAbandoned())

        coVerify(exactly = 0) { passwordRepository.deleteByUserIdInAndSessionIdIsNotNull(any()) }
        coVerify(exactly = 0) { userRepository.deleteByIdInAndSessionIdIsNotNull(any()) }
    }

    private fun abandonedUser(id: UUID) = UserEntity(
        status = "ENABLED",
        creationDate = LocalDateTime.now(),
        sessionId = UUID.randomUUID()
    ).apply { this.id = id }

    private fun provisionalUser() {
        coEvery { userRepository.findByIdAndSessionId(userId, sessionId) } returns UserEntity(
            status = "ENABLED",
            creationDate = LocalDateTime.now(),
            sessionId = sessionId
        )
    }

    private fun noIdentifierClaim() {
        every { claimManager.listIdentifierClaims() } returns emptyList()
        coEvery { userManager.findTakenIdentifierOrNull(userId, emptyList(), emptyMap()) } returns null
    }

    @Test
    fun `promote - Proceeds when the identifier the account holds is still free`() = runTest {
        provisionalUser()
        identifierClaims("email")
        coEvery {
            collectedClaimRepository.findByUserIdAndClaimInList(userId, listOf("email"))
        } returns listOf(claimEntity("email", "free@example.com"))
        stubFoldOf("email", "free@example.com")
        coEvery {
            userManager.findTakenIdentifierOrNull(
                userId, listOf("email"), mapOf("email" to "free@example.com")
            )
        } returns null
        coEvery { providerUserInfoRepository.findByUserId(userId) } returns emptyList()
        coEvery { passwordRepository.clearSessionId(userId, sessionId) } returns 1
        coEvery { collectedClaimRepository.clearSessionId(userId, sessionId) } returns 1
        coEvery { providerUserInfoRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { totpEnrollmentRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { userRepository.clearSessionId(userId, sessionId) } returns 1

        manager.promote(sessionId, userId)

        coVerify { userRepository.clearSessionId(userId, sessionId) }
        coVerifyOrder {
            objectLockRepository.lock(LockKey.IdentifierValue("free@example.com").stripe)
            userManager.findTakenIdentifierOrNull(any(), any(), any())
            userRepository.clearSessionId(userId, sessionId)
        }
    }

    @Test
    fun `promote - Takes no lock for an account holding no identity to take`() = runTest {
        provisionalUser()
        noIdentifierClaim()
        coEvery { providerUserInfoRepository.findByUserId(userId) } returns emptyList()
        coEvery { passwordRepository.clearSessionId(userId, sessionId) } returns 1
        coEvery { collectedClaimRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { providerUserInfoRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { totpEnrollmentRepository.clearSessionId(userId, sessionId) } returns 0
        coEvery { userRepository.clearSessionId(userId, sessionId) } returns 1

        manager.promote(sessionId, userId)

        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    private fun identifierClaims(vararg ids: String) {
        every { claimManager.listIdentifierClaims() } returns ids.map { id ->
            mockk<Claim> { every { this@mockk.id } returns id }
        }
    }

    /**
     * The claim [claim] of the account being promoted, and the mapper answering the folded value the
     * promotion recovers from it — the row carries a key, not the fold.
     */
    private fun stubFoldOf(claim: String, value: String) {
        every { claimManager.findByIdOrNull(claim) } returns mockk { every { dataType } returns ClaimDataType.STRING }
        every { claimValueMapper.toFoldedValueOfStored(value, ClaimDataType.STRING) } returns value
    }

    private fun claimEntity(claim: String, value: String) = CollectedClaimEntity(
        userId = userId,
        claim = claim,
        value = value,
        foldedEqualityHash = value.hashCode().toLong(),
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
}

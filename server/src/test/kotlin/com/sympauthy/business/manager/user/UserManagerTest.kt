package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class UserManagerTest {

    private val emailClaim = OpenIdConnectClaimId.EMAIL
    private val phoneClaim = OpenIdConnectClaimId.PHONE_NUMBER

    /** The two values as `collected_claims` spells them, which is the spelling every caller offers. */
    private val storedAddress = "\"user@example.com\""
    private val storedNumber = "\"+33612345678\""

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var userMapper: UserMapper

    @SpyK
    @InjectMockKs
    lateinit var manager: UserManager

    @Test
    fun `findByIdOrNull - Return user when found`() = runTest {
        val userId = UUID.randomUUID()
        val entity = mockk<UserEntity>()
        val user = mockk<User>()

        coEvery { userRepository.findByIdAndSessionIdIsNull(userId) } returns entity
        every { userMapper.toUser(entity) } returns user

        val result = manager.findByIdOrNull(userId)

        assertSame(user, result)
    }

    @Test
    fun `findByIdOrNull - Return null when user not found`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { userRepository.findByIdAndSessionIdIsNull(userId) } returns null

        val result = manager.findByIdOrNull(userId)

        assertNull(result)
    }

    @Test
    fun `findByIdOrNull - Return null when id is null`() = runTest {
        val result = manager.findByIdOrNull(null)

        assertNull(result)
    }

    @Test
    fun `findById - Return user when found`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockk<User>()

        coEvery { manager.findByIdOrNull(userId) } returns user

        val result = manager.findById(userId)

        assertSame(user, result)
    }

    @Test
    fun `findById - Throw exception when user not found`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { manager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<BusinessException> {
            manager.findById(userId)
        }

        assertEquals("user.not_found", exception.detailsId)
        assertEquals(userId.toString(), exception.values["userId"])
    }

    @Test
    fun `findById - Throw exception when id is null`() = runTest {
        coEvery { manager.findByIdOrNull(null) } returns null

        val exception = assertThrows<BusinessException> {
            manager.findById(null)
        }

        assertEquals("user.not_found", exception.detailsId)
        assertEquals("null", exception.values["userId"])
    }

    @Test
    fun `createUser - Create and return new user with ENABLED status`() = runTest {
        val entitySlot = slot<UserEntity>()
        val savedEntity = mockk<UserEntity>()
        val user = mockk<User>()

        coEvery { userRepository.save(capture(entitySlot)) } answers {
            savedEntity
        }
        every { userMapper.toUser(savedEntity) } returns user

        val result = manager.createUser(sessionId = null)

        assertSame(user, result)
        assertEquals(UserStatus.ENABLED.name, entitySlot.captured.status)
        assertNotNull(entitySlot.captured.creationDate)
    }

    @Test
    fun `checkPromoted - Passes for a committed account`() = runTest {
        val userId = UUID.randomUUID()
        val entity = mockk<UserEntity>()

        coEvery { userRepository.findById(userId) } returns entity
        every { userMapper.toUser(entity) } returns committedUser(userId)

        manager.checkPromoted(userId)
    }

    @Test
    fun `checkPromoted - Refuses an account a session is still signing up`() = runTest {
        val userId = UUID.randomUUID()
        val entity = mockk<UserEntity>()

        coEvery { userRepository.findById(userId) } returns entity
        every { userMapper.toUser(entity) } returns provisionalUser(userId)

        val exception = assertThrows<BusinessException> { manager.checkPromoted(userId) }

        assertEquals("user.not_promoted", exception.detailsId)
        assertFalse(exception.recoverable)
        assertEquals(userId.toString(), exception.values["userId"])
    }

    @Test
    fun `checkPromoted - Refuses an id naming no account at all`() = runTest {
        val userId = UUID.randomUUID()

        coEvery { userRepository.findById(userId) } returns null

        val exception = assertThrows<BusinessException> { manager.checkPromoted(userId) }

        assertEquals("user.not_found", exception.detailsId)
    }

    @Test
    fun `checkPromoted - Refuses a row the mapper cannot read back`() = runTest {
        val userId = UUID.randomUUID()
        val entity = mockk<UserEntity>()
        val refusal = mockk<BusinessException>()

        coEvery { userRepository.findById(userId) } returns entity
        every { userMapper.toUser(entity) } throws refusal

        assertSame(refusal, assertThrows<BusinessException> { manager.checkPromoted(userId) })
    }

    @Test
    fun `checkPromoted - Reads the account the caller already holds without a query`() {
        val userId = UUID.randomUUID()

        manager.checkPromoted(committedUser(userId))

        val exception = assertThrows<BusinessException> { manager.checkPromoted(provisionalUser(userId)) }
        assertEquals("user.not_promoted", exception.detailsId)
    }

    @Test
    fun `findTakenIdentifierOrNull - Asks nothing when no claim or no value is offered`() = runTest {
        val userId = UUID.randomUUID()

        assertNull(manager.findTakenIdentifierOrNull(userId, emptyList(), mapOf(emailClaim to storedAddress)))
        assertNull(manager.findTakenIdentifierOrNull(userId, listOf(emailClaim), emptyMap()))
    }

    @Test
    fun `findTakenIdentifierOrNull - Answers the offered claim, not the conflicting row's`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        committedRows(claimRow(ownerId, phoneClaim, storedAddress))

        val taken = manager.findTakenIdentifierOrNull(
            userId, listOf(emailClaim, phoneClaim), mapOf(emailClaim to storedAddress)
        )

        // The claim is the caller's own, which it already knows; the account is the other one, which is
        // the half only this read can answer.
        assertEquals(TakenIdentifier(claimId = emailClaim, userId = ownerId), taken)
    }

    @Test
    fun `findTakenIdentifierOrNull - Names the one taken value though the others are free`() = runTest {
        val userId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        committedRows(claimRow(ownerId, emailClaim, storedAddress))

        // The rule is any of the offered values under any identifier claim, and not an account holding all
        // of them: the account owning one owns the identity whether or not it owns the rest.
        val taken = manager.findTakenIdentifierOrNull(
            userId,
            listOf(emailClaim, phoneClaim),
            mapOf(emailClaim to storedAddress, phoneClaim to storedNumber)
        )

        assertEquals(TakenIdentifier(claimId = emailClaim, userId = ownerId), taken)
    }

    @Test
    fun `findTakenIdentifierOrNull - Passes over a row the account holds under that same claim`() =
        runTest {
            val userId = UUID.randomUUID()
            committedRows(claimRow(userId, emailClaim, storedAddress))

            val taken = manager.findTakenIdentifierOrNull(
                userId, listOf(emailClaim, phoneClaim), mapOf(emailClaim to storedAddress)
            )

            assertNull(taken)
        }

    @Test
    fun `findTakenIdentifierOrNull - Passes over a row the account holds under another claim`() =
        runTest {
            val userId = UUID.randomUUID()
            committedRows(claimRow(userId, phoneClaim, storedAddress))

            // Its own under a second claim, and not a conflict: both rows name the one account, so a login
            // over that value resolves to it either way and there is no pair to exclude.
            val taken = manager.findTakenIdentifierOrNull(
                userId, listOf(emailClaim, phoneClaim), mapOf(emailClaim to storedAddress)
            )

            assertNull(taken)
        }

    @Test
    fun `findTakenIdentifierOrNull - Refuses the crossed pair that would sign one owner in as another`() =
        runTest {
            val userId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            // Another account already holds, under its username, the address this one offers as its email.
            // Allowing it leaves each value matching a row of each account, and whoever owns one of them is
            // resolved to the other account.
            committedRows(claimRow(ownerId, phoneClaim, storedAddress))

            val taken = manager.findTakenIdentifierOrNull(
                userId,
                listOf(emailClaim, phoneClaim),
                mapOf(emailClaim to storedAddress, phoneClaim to storedNumber)
            )

            assertEquals(TakenIdentifier(claimId = emailClaim, userId = ownerId), taken)
        }

    @Test
    fun `findTakenIdentifierOrNull - Exempts nothing for a caller holding no account yet`() = runTest {
        val ownerId = UUID.randomUUID()
        committedRows(claimRow(ownerId, emailClaim, storedAddress))

        val taken = manager.findTakenIdentifierOrNull(
            null, listOf(emailClaim), mapOf(emailClaim to storedAddress)
        )

        assertEquals(TakenIdentifier(claimId = emailClaim, userId = ownerId), taken)
    }

    private fun committedRows(vararg rows: CollectedClaimEntity) {
        every {
            collectedClaimRepository.findAll(any<PredicateSpecification<CollectedClaimEntity>>())
        } returns rows.asList().asFlow()
    }

    /** The values are the ones `collected_claims` holds, quotes included, which is what the rows compare on. */
    private fun claimRow(userId: UUID, claim: String, value: String) = CollectedClaimEntity(
        userId = userId,
        claim = claim,
        value = value,
        comparisonValue = value,
        verified = null,
        collectionDate = LocalDateTime.now(),
        verificationDate = null,
        sessionId = null
    )

    private fun committedUser(id: UUID) = User(
        id = id,
        status = UserStatus.ENABLED,
        creationDate = LocalDateTime.now(),
        sessionId = null
    )

    private fun provisionalUser(id: UUID) = User(
        id = id,
        status = UserStatus.ENABLED,
        creationDate = LocalDateTime.now(),
        sessionId = UUID.randomUUID()
    )
}

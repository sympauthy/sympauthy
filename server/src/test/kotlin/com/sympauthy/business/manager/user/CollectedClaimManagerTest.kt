package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.HeldStripes
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.CollectedClaimUpdateMapper
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.ObjectLockRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class CollectedClaimManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var userManager: UserManager

    private val objectLockRepository = mockk<ObjectLockRepository>(relaxed = true)

    /**
     * The real manager over the stripe table, so a test can say which row was taken. A double answering for
     * `withLock` would run the block whether or not anything was ever locked, and the key naming the value
     * as `collected_claims` spells it is half of what makes this agree with the promotion.
     */
    var lockManager: LockManager = LockManager(objectLockRepository, HeldStripes())

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var collectedClaimMapper: CollectedClaimMapper

    @MockK
    lateinit var collectedClaimUpdateMapper: CollectedClaimUpdateMapper

    @MockK
    lateinit var claimValueValidator: ClaimValueValidator

    @MockK
    lateinit var claimValueMapper: ClaimValueMapper

    @SpyK
    @InjectMockKs
    lateinit var manager: CollectedClaimManager

    @Test
    fun `findByUserId - Return collected claims for user`() = runTest {
        val userId = UUID.randomUUID()
        val entity1 = mockk<CollectedClaimEntity>()
        val entity2 = mockk<CollectedClaimEntity>()
        val collectedClaim1 = mockk<CollectedClaim>()
        val collectedClaim2 = mockk<CollectedClaim>()

        coEvery { collectedClaimRepository.findByUserId(userId) } returns listOf(entity1, entity2)
        every { collectedClaimMapper.toCollectedClaim(entity1) } returns collectedClaim1
        every { collectedClaimMapper.toCollectedClaim(entity2) } returns collectedClaim2

        val result = manager.findByUserId(userId)

        assertEquals(2, result.count())
        assertSame(collectedClaim1, result[0])
        assertSame(collectedClaim2, result[1])
    }

    @Test
    fun `findByUserId - Filter out null values from mapper`() = runTest {
        val userId = UUID.randomUUID()
        val entity1 = mockk<CollectedClaimEntity>()
        val entity2 = mockk<CollectedClaimEntity>()
        val collectedClaim1 = mockk<CollectedClaim>()

        coEvery { collectedClaimRepository.findByUserId(userId) } returns listOf(entity1, entity2)
        every { collectedClaimMapper.toCollectedClaim(entity1) } returns collectedClaim1
        every { collectedClaimMapper.toCollectedClaim(entity2) } returns null

        val result = manager.findByUserId(userId)

        assertEquals(1, result.count())
        assertSame(collectedClaim1, result[0])
    }

    @Test
    fun `areAllRequiredClaimCollected - True if all required claims are collected false otherwise`() {
        val firstRequiredClaim = mockk<Claim>()
        val secondRequiredClaim = mockk<Claim>()
        val optionalClaim = mockk<Claim>()

        val firstRequiredCollectedClaim = mockk<CollectedClaim> {
            every { claim } returns firstRequiredClaim
        }
        val secondRequiredCollectedClaim = mockk<CollectedClaim> {
            every { claim } returns secondRequiredClaim
        }
        val optionalCollectedClaim = mockk<CollectedClaim> {
            every { claim } returns optionalClaim
        }

        every { claimManager.listRequiredClaims() } returns listOf(firstRequiredClaim, secondRequiredClaim)

        assertTrue(
            manager.areAllRequiredClaimCollected(
                listOf(
                    firstRequiredCollectedClaim,
                    secondRequiredCollectedClaim
                )
            )
        )

        assertFalse(manager.areAllRequiredClaimCollected(listOf()))
        assertFalse(manager.areAllRequiredClaimCollected(listOf(firstRequiredCollectedClaim)))
        assertFalse(manager.areAllRequiredClaimCollected(listOf(secondRequiredCollectedClaim)))
        assertFalse(manager.areAllRequiredClaimCollected(listOf(optionalCollectedClaim)))
    }

    @Test
    fun `areAllRequiredClaimCollected - Always true if not required claims`() {
        every { claimManager.listRequiredClaims() } returns emptyList()

        assertTrue(manager.areAllRequiredClaimCollected(listOf()))
        assertTrue(manager.areAllRequiredClaimCollected(listOf(mockk())))
    }

    @Test
    fun `writeUpdates - Return the created, the updated and the untouched claims`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns userId
        }
        val updatedClaim = "updated"
        val deletedClaim = "deleted"
        val keptClaim = "kept"

        // Nothing looks at which claim the created entity carries; it is mapped and returned.
        val createdEntity = mockk<CollectedClaimEntity>()
        val updatedEntity = mockk<CollectedClaimEntity> {
            every { claim } returns updatedClaim
        }
        val deletedEntity = mockk<CollectedClaimEntity> {
            every { claim } returns deletedClaim
        }
        val keptEntity = mockk<CollectedClaimEntity> {
            every { claim } returns keptClaim
        }

        val created = mockk<CollectedClaim>()
        val updated = mockk<CollectedClaim>()
        val kept = mockk<CollectedClaim>()

        // The updates are handed straight to the three helpers below, which are stubbed on the spy.
        val updates = List(3) { mockk<CollectedClaimUpdate>() }

        coEvery { collectedClaimRepository.findByUserId(userId) } returns
                listOf(updatedEntity, deletedEntity, keptEntity)
        coEvery { manager.createMissingClaims(user, any(), updates) } returns listOf(createdEntity)
        coEvery { manager.updateExistingClaims(any(), updates) } returns listOf(updatedEntity)
        coEvery { manager.deleteExistingClaimsUpdatedToNull(any(), updates) } returns listOf(deletedEntity)
        every { collectedClaimMapper.toCollectedClaim(createdEntity) } returns created
        every { collectedClaimMapper.toCollectedClaim(updatedEntity) } returns updated
        every { collectedClaimMapper.toCollectedClaim(keptEntity) } returns kept

        val result = manager.writeUpdates(
            user = user,
            applicableUpdates = updates
        )

        assertEquals(3, result.count())
        assertTrue(result.contains(created))
        assertTrue(result.contains(updated))
        assertTrue(result.contains(kept))
    }

    @Test
    fun `deleteExistingClaimUpdatedToNull - Delete existing claim`() = runTest {
        val deletedClaim = "delete"
        val toDeleteClaimEntity = mockk<CollectedClaimEntity>()
        val update = mockUpdate(deletedClaim, null)

        coEvery { collectedClaimRepository.deleteAll(listOf(toDeleteClaimEntity)) } returns 1

        val result = manager.deleteExistingClaimsUpdatedToNull(
            existingEntityByClaimMap = mapOf(
                deletedClaim to toDeleteClaimEntity
            ),
            applicableUpdates = listOf(update)
        )

        assertEquals(1, result.count())
        assertSame(toDeleteClaimEntity, result.getOrNull(0))
    }

    @Test
    fun `deleteExistingClaimUpdatedToNull - Do not delete if value for update is not null`() = runTest {
        val updatedClaim = "udpated"
        val updatedEntity = mockk<CollectedClaimEntity>()
        val update = mockUpdateOfValue(mockk())

        coEvery { collectedClaimRepository.deleteAll(emptyList()) } returns 0

        val result = manager.deleteExistingClaimsUpdatedToNull(
            existingEntityByClaimMap = mapOf(
                updatedClaim to updatedEntity
            ),
            applicableUpdates = listOf(update)
        )

        assertEquals(0, result.count())
    }

    @Test
    fun `deleteExistingClaimUpdatedToNull - Do not delete if no update for claim`() = runTest {
        val keptClaim = "kept"
        val keptEntity = mockk<CollectedClaimEntity>()
        val update = mockUpdate("deleted", null)

        coEvery { collectedClaimRepository.deleteAll(emptyList()) } returns 0

        val result = manager.deleteExistingClaimsUpdatedToNull(
            existingEntityByClaimMap = mapOf(
                keptClaim to keptEntity
            ),
            applicableUpdates = listOf(update)
        )

        assertEquals(0, result.count())
    }

    @Test
    fun `updateExistingClaims - Update claim only if value changed`() = runTest {
        val updatedClaim = "update"
        val newValue = "new"
        val newValueOptional = Optional.of<Any>(newValue)
        val toUpdateEntity = mockk<CollectedClaimEntity> {
            every { value } returns "old"
        }
        val update = mockUpdate(updatedClaim, newValueOptional)

        every { collectedClaimUpdateMapper.toValue(Optional.of(newValue)) } answers { newValue }
        every { collectedClaimUpdateMapper.updateEntity(toUpdateEntity, update) } returns toUpdateEntity
        every { collectedClaimRepository.updateAll(listOf(toUpdateEntity)) } returns flowOf(toUpdateEntity)

        val result = manager.updateExistingClaims(
            existingEntityByClaimMap = mapOf(
                updatedClaim to toUpdateEntity
            ),
            applicableUpdates = listOf(update)
        )

        assertEquals(1, result.count())
        assertSame(toUpdateEntity, result.getOrNull(0))
    }

    @Test
    fun `updateExistingClaims - Do not update claim if value did not change`() = runTest {
        val updatedClaim = "update"
        val updatedClaimValue = "old"
        val newValueOptional = Optional.of<Any>(updatedClaimValue)
        val toUpdateEntity = mockk<CollectedClaimEntity> {
            every { value } returns "old"
        }
        val update = mockUpdate(updatedClaim, newValueOptional)

        every { collectedClaimUpdateMapper.toValue(Optional.of(updatedClaimValue)) } answers { updatedClaimValue }
        every { collectedClaimRepository.updateAll(emptyList()) } returns flowOf()

        val result = manager.updateExistingClaims(
            existingEntityByClaimMap = mapOf(
                updatedClaim to toUpdateEntity
            ),
            applicableUpdates = listOf(update)
        )

        assertEquals(0, result.count())
    }

    @Test
    fun `updateExistingClaims - Do not change if no update on existing claims`() = runTest {
        val nonUpdatedClaim = "non-updated"
        val nonUpdatedEntity = mockk<CollectedClaimEntity>()
        val update = mockUpdate("updated", mockk())

        coEvery { collectedClaimRepository.updateAll(emptyList()) } returns flowOf()

        val result = manager.updateExistingClaims(
            existingEntityByClaimMap = mapOf(
                nonUpdatedClaim to nonUpdatedEntity
            ),
            applicableUpdates = listOf(update)
        )

        assertEquals(0, result.count())
    }

    @Test
    fun `createMissingClaims - Create existing claims`() = runTest {
        val userId = UUID.randomUUID()
        val createdClaim = "updated"
        val createdEntity = mockk<CollectedClaimEntity>()
        val update = mockUpdate(createdClaim, mockk())

        every { collectedClaimUpdateMapper.toEntity(userId, null, update) } returns createdEntity
        every { collectedClaimRepository.saveAll(listOf(createdEntity)) } returns flowOf(createdEntity)

        val result = manager.createMissingClaims(
            user = mockk {
                every { id } returns userId
                every { sessionId } returns null
            },
            existingEntityByClaimMap = emptyMap(),
            applicableUpdates = listOf(update)
        )

        assertEquals(1, result.count())
        assertSame(createdEntity, result.getOrNull(0))
    }

    @Test
    fun `createMissingClaims - Do not create existing claims`() = runTest {
        val updatedClaim = "updated"
        val updatedEntity = mockk<CollectedClaimEntity>()
        val update = mockUpdate(updatedClaim, mockk())

        every { collectedClaimRepository.saveAll(emptyList()) } returns flowOf()

        val result = manager.createMissingClaims(
            user = mockk(),
            existingEntityByClaimMap = mapOf(
                updatedClaim to updatedEntity
            ),
            applicableUpdates = listOf(update)
        )

        assertEquals(0, result.count())
    }

    @Test
    fun `applyUpdates - Refuse an identifier value another committed account holds`() = runTest {
        val user = committedUser()
        val emailClaim = mockEmailClaim()
        val update = mockUpdateOfClaim(emailClaim, Optional.of(EMAIL))

        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
        every { collectedClaimUpdateMapper.toComparisonValue(update.value) } returns COMPARED_EMAIL
        coEvery {
            userManager.findTakenIdentifierOrNull(
                user.id, listOf(EMAIL_CLAIM), mapOf(EMAIL_CLAIM to COMPARED_EMAIL)
            )
        } returns TakenIdentifier(claimId = EMAIL_CLAIM, userId = ownerId)

        // The repository is left unstubbed: reaching the assertion is proof nothing was written.
        val exception = assertThrows<BusinessException> {
            manager.applyUpdates(user, listOf(update))
        }

        assertEquals("user.claims.identifier_taken", exception.detailsId)
        assertEquals(EMAIL_CLAIM, exception.values["claim"])
        assertEquals(ownerId.toString(), exception.values["userId"])
        coVerify { objectLockRepository.lock(LockKey.IdentifierValue(COMPARED_EMAIL).stripe) }
    }

    @Test
    fun `applyUpdates - Write an identifier value no other committed account holds`() = runTest {
        val user = committedUser()
        val emailClaim = mockEmailClaim()
        val update = mockUpdateOfClaim(emailClaim, Optional.of(EMAIL))
        val collectedClaim = mockk<CollectedClaim>()

        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
        every { collectedClaimUpdateMapper.toComparisonValue(update.value) } returns COMPARED_EMAIL
        coEvery {
            userManager.findTakenIdentifierOrNull(
                user.id, listOf(EMAIL_CLAIM), mapOf(EMAIL_CLAIM to COMPARED_EMAIL)
            )
        } returns null
        coEvery { manager.writeUpdates(user, listOf(update)) } returns listOf(collectedClaim)

        val result = manager.applyUpdates(user, listOf(update))

        assertEquals(1, result.count())
        assertSame(collectedClaim, result[0])
        coVerify { objectLockRepository.lock(LockKey.IdentifierValue(COMPARED_EMAIL).stripe) }
    }

    @Test
    fun `applyUpdates - Offer the value under the claim being written, and search every identifier claim`() =
        runTest {
        val user = committedUser()
        val phoneClaim = mockk<Claim> {
            every { id } returns PHONE_CLAIM
        }
        val update = mockUpdateOfClaim(phoneClaim, Optional.of(EMAIL))

        every { claimManager.listIdentifierClaims() } returns listOf(mockEmailClaim(), phoneClaim)
        every { collectedClaimUpdateMapper.toComparisonValue(update.value) } returns COMPARED_EMAIL
        coEvery {
            userManager.findTakenIdentifierOrNull(
                user.id, listOf(EMAIL_CLAIM, PHONE_CLAIM), mapOf(PHONE_CLAIM to COMPARED_EMAIL)
            )
        } returns TakenIdentifier(claimId = PHONE_CLAIM, userId = ownerId)

        // What the rule answers is passed through as it stands; which rows it counts as a conflict, and
        // which of the account's own it never does, is UserManagerTest's.
        val exception = assertThrows<BusinessException> {
            manager.applyUpdates(user, listOf(update))
        }

        assertEquals("user.claims.identifier_taken", exception.detailsId)
        assertEquals(PHONE_CLAIM, exception.values["claim"])
        assertEquals(ownerId.toString(), exception.values["userId"])
    }

    @Test
    fun `applyUpdates - Ask nothing of an update that touches no identifier claim`() = runTest {
        val user = mockk<User> {
            every { sessionId } returns null
        }
        // The update's value is never read: its claim is not an identifier, so it is filtered out first.
        val update = mockk<CollectedClaimUpdate> {
            every { claim } returns mockk()
        }
        val collectedClaim = mockk<CollectedClaim>()

        every { claimManager.listIdentifierClaims() } returns listOf(mockk())
        coEvery { manager.writeUpdates(user, listOf(update)) } returns listOf(collectedClaim)

        val result = manager.applyUpdates(user, listOf(update))

        assertEquals(1, result.count())
        assertSame(collectedClaim, result[0])
        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    @Test
    fun `applyUpdates - Ask nothing at all of a write to a provisional account`() = runTest {
        val user = mockk<User> {
            every { sessionId } returns UUID.randomUUID()
        }
        val update = mockk<CollectedClaimUpdate>()
        val collectedClaim = mockk<CollectedClaim>()

        // The identifier claims are left unstubbed: a provisional write does not even read them, so the
        // sign-up path pays nothing for a check its promotion is going to run.
        coEvery { manager.writeUpdates(user, listOf(update)) } returns listOf(collectedClaim)

        val result = manager.applyUpdates(user, listOf(update))

        assertEquals(1, result.count())
        assertSame(collectedClaim, result[0])
        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    @Test
    fun `applyUpdates - Ask nothing of an identifier claim being cleared`() = runTest {
        val user = mockk<User> {
            every { sessionId } returns null
        }
        // The claim's id is never read: the update carries no value, so it is dropped before the check.
        val identifierClaim = mockk<Claim>()
        val update = mockUpdateOfClaim(identifierClaim, null)
        val collectedClaim = mockk<CollectedClaim>()

        every { claimManager.listIdentifierClaims() } returns listOf(identifierClaim)
        every { collectedClaimUpdateMapper.toComparisonValue(null) } returns null
        coEvery { manager.writeUpdates(user, listOf(update)) } returns listOf(collectedClaim)

        val result = manager.applyUpdates(user, listOf(update))

        assertEquals(1, result.count())
        assertSame(collectedClaim, result[0])
        coVerify(exactly = 0) { objectLockRepository.lock(any()) }
    }

    @Test
    fun `getComparisonValueOf - Cleans the value under the claim, then folds it to plain text`() {
        // The claim is never named here: spelling a value is a question about the value, not about which
        // claim is asking. The quoting `value` carries is not part of it either.
        val emailClaim = mockk<Claim>()
        every {
            claimValueValidator.cleanValueForClaimOrNull(emailClaim, " Someone@Example.COM ")
        } returns "Someone@Example.COM"
        every { claimValueMapper.toComparisonValue("Someone@Example.COM") } returns COMPARED_EMAIL

        assertEquals(COMPARED_EMAIL, manager.getComparisonValueOf(emailClaim, " Someone@Example.COM "))
    }

    @Test
    fun `getComparisonValueOf - Answers none where the claim could hold no such value`() {
        val emailClaim = mockk<Claim>()
        every { claimValueValidator.cleanValueForClaimOrNull(emailClaim, "not-an-address") } returns null

        // The mapper is never reached: a value the claim would refuse matches no row of it either.
        assertNull(manager.getComparisonValueOf(emailClaim, "not-an-address"))
    }

    @Test
    fun `getIdentifierComparisonValuesOf - Spells one value under every identifier claim, by the claim holding it`() {
        val emailClaim = mockEmailClaim()
        val usernameClaim = mockk<Claim> { every { id } returns "preferred_username" }
        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim, usernameClaim)
        every { claimValueValidator.cleanValueForClaimOrNull(emailClaim, EMAIL) } returns EMAIL
        every { claimValueValidator.cleanValueForClaimOrNull(usernameClaim, EMAIL) } returns EMAIL
        every { claimValueMapper.toComparisonValue(EMAIL) } returns COMPARED_EMAIL

        assertEquals(
            mapOf(EMAIL_CLAIM to COMPARED_EMAIL, "preferred_username" to COMPARED_EMAIL),
            manager.getIdentifierComparisonValuesOf(EMAIL)
        )
    }

    @Test
    fun `getIdentifierComparisonValuesOf - Drops a claim that could hold no such value`() {
        val emailClaim = mockEmailClaim()
        val numberClaim = mockk<Claim>()
        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim, numberClaim)
        every { claimValueValidator.cleanValueForClaimOrNull(emailClaim, EMAIL) } returns EMAIL
        every { claimValueValidator.cleanValueForClaimOrNull(numberClaim, EMAIL) } returns null
        every { claimValueMapper.toComparisonValue(EMAIL) } returns COMPARED_EMAIL

        // Absent rather than present with nothing: no row of that claim holds a value it would refuse.
        assertEquals(mapOf(EMAIL_CLAIM to COMPARED_EMAIL), manager.getIdentifierComparisonValuesOf(EMAIL))
    }

    @Test
    fun `getIdentifierComparisonValuesIn - Keeps an identifier claim's value, as the rows compare on it`() {
        val emailClaim = mockEmailClaim()
        val update = mockUpdateOfClaim(emailClaim, Optional.of(EMAIL))
        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
        every { collectedClaimUpdateMapper.toComparisonValue(Optional.of(EMAIL)) } returns COMPARED_EMAIL

        assertEquals(mapOf(EMAIL_CLAIM to COMPARED_EMAIL), manager.getIdentifierComparisonValuesIn(listOf(update)))
    }

    @Test
    fun `getIdentifierComparisonValuesIn - Drops a claim that is not an identifier one`() {
        every { claimManager.listIdentifierClaims() } returns listOf(mockk())

        // Nothing about the update is stubbed: one whose claim is not in the set is dropped before either
        // its claim id or its value is read, which is the whole of what this holds.
        assertTrue(manager.getIdentifierComparisonValuesIn(listOf(mockk(relaxed = true))).isEmpty())
    }

    @Test
    fun `getIdentifierComparisonValuesIn - Drops an update clearing a claim, which takes no value from anybody`() {
        val emailClaim = mockk<Claim>()
        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
        every { collectedClaimUpdateMapper.toComparisonValue(null) } returns null

        assertTrue(manager.getIdentifierComparisonValuesIn(listOf(mockUpdateOfClaim(emailClaim, null))).isEmpty())
    }

    @Test
    fun `getIdentifierComparisonValuesIn - Answers nothing when the deployment names no identifier claim`() {
        every { claimManager.listIdentifierClaims() } returns emptyList()

        // The updates are never looked at: with no identifier claim configured there is nothing to match
        // them against, and the read below them never runs.
        assertTrue(manager.getIdentifierComparisonValuesIn(listOf(mockk())).isEmpty())
    }

    /** The account a refusal names as holding the value, which reaches an operator and never the person refused. */
    private val ownerId = UUID.randomUUID()

    private fun committedUser(): User = mockk {
        every { id } returns UUID.randomUUID()
        every { sessionId } returns null
    }

    private fun mockEmailClaim(): Claim = mockk {
        every { id } returns EMAIL_CLAIM
    }

    private fun mockUpdateOfClaim(
        updateClaim: Claim,
        updateValue: Optional<Any>?
    ): CollectedClaimUpdate = mockUpdateOfValue(updateValue).also { update ->
        every { update.claim } returns updateClaim
    }

    /** An update whose value is read before, and sometimes instead of, the claim it is for. */
    private fun mockUpdateOfValue(updateValue: Optional<Any>?): CollectedClaimUpdate = mockk(relaxed = true) {
        every { value } returns updateValue
    }

    private fun mockUpdate(
        updateClaim: String,
        updateValue: Optional<Any>? = null
    ): CollectedClaimUpdate = mockUpdateOfValue(updateValue).also { update ->
        every { update.claim } returns mockk { every { id } returns updateClaim }
    }

    private companion object {
        const val EMAIL_CLAIM = "email"
        const val PHONE_CLAIM = "phone_number"
        const val EMAIL = "someone@example.com"

        /** The value as `collected_claims` spells it, which is what both the key and the check compare on. */
        const val STORED_EMAIL = "\"someone@example.com\""

        /** The same address in the spelling `collected_claims` compares on: plain text, lowercased. */
        const val COMPARED_EMAIL = "someone@example.com"
    }
}

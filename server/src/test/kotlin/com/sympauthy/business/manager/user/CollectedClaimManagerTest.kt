package com.sympauthy.business.manager.user

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.CollectedClaimUpdateMapper
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import io.mockk.coEvery
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
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class CollectedClaimManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var collectedClaimMapper: CollectedClaimMapper

    @MockK
    lateinit var collectedClaimUpdateMapper: CollectedClaimUpdateMapper

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
    fun applyUpdates() = runTest {
        val userId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns userId
        }
        val createdClaim = "created"
        val updatedClaim = "updated"
        val deletedClaim = "deleted"
        val keptClaim = "kept"

        val createdEntity = mockk<CollectedClaimEntity> {
            every { claim } returns createdClaim
        }
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

        val createdUpdate = mockUpdate(createdClaim)
        val updatedUpdate = mockUpdate(updatedClaim)
        val deleteUpdate = mockUpdate(deletedClaim)
        val updates = listOf(createdUpdate, updatedUpdate, deleteUpdate)

        coEvery { collectedClaimRepository.findByUserId(userId) } returns
                listOf(updatedEntity, deletedEntity, keptEntity)
        coEvery { manager.createMissingClaims(user, any(), updates) } returns listOf(createdEntity)
        coEvery { manager.updateExistingClaims(any(), updates) } returns listOf(updatedEntity)
        coEvery { manager.deleteExistingClaimsUpdatedToNull(any(), updates) } returns listOf(deletedEntity)
        every { collectedClaimMapper.toCollectedClaim(createdEntity) } returns created
        every { collectedClaimMapper.toCollectedClaim(updatedEntity) } returns updated
        every { collectedClaimMapper.toCollectedClaim(keptEntity) } returns kept

        val result = manager.applyUpdates(
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
        val update = mockUpdate("updated", mockk())

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
        every { collectedClaimUpdateMapper.updateEntity(toUpdateEntity, update) } returns toUpdateEntity
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

        every { collectedClaimUpdateMapper.toEntity(userId, update) } returns createdEntity
        every { collectedClaimRepository.saveAll(listOf(createdEntity)) } returns flowOf(createdEntity)

        val result = manager.createMissingClaims(
            user = mockk {
                every { id } returns userId
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

    private fun mockUpdate(
        updateClaim: String,
        updateValue: Optional<Any>? = null
    ): CollectedClaimUpdate {
        val updateClaimObject = mockk<Claim> {
            every { id } returns updateClaim
        }
        return mockk(relaxed = true) {
            every { claim } returns updateClaimObject
            every { value } returns updateValue
        }
    }
}

package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.UserEntity
import com.sympauthy.data.repository.CollectedClaimRepository
import com.sympauthy.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class UserSearchManagerTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var claimValueValidator: ClaimValueValidator

    @MockK
    lateinit var userMapper: UserMapper

    @MockK
    lateinit var collectedClaimMapper: CollectedClaimMapper

    @InjectMockKs
    lateinit var manager: UserSearchManager

    private fun mockClaim(id: String, enabled: Boolean = true, dataType: ClaimDataType = ClaimDataType.STRING): Claim {
        return mockk<Claim> {
            every { this@mockk.id } returns id
            every { this@mockk.enabled } returns enabled
            every { this@mockk.dataType } returns dataType
        }
    }

    private fun mockUser(
        status: UserStatus = UserStatus.ENABLED,
        creationDate: LocalDateTime = LocalDateTime.now()
    ): User {
        return User(
            id = UUID.randomUUID(),
            status = status,
            creationDate = creationDate
        )
    }

    private fun mockCollectedClaim(userId: UUID, claim: Claim, value: Any?): CollectedClaim {
        return CollectedClaim(
            userId = userId,
            claim = claim,
            value = value,
            verified = null,
            collectionDate = LocalDateTime.now(),
            verificationDate = null
        )
    }

    @Test
    fun `listUsers - returns all users when no filters`() = runTest {
        val emailClaim = mockClaim("email")
        val user1 = mockUser()
        val user2 = mockUser()
        val entity1 = mockk<UserEntity>()
        val entity2 = mockk<UserEntity>()
        val claimEntity = mockk<CollectedClaimEntity>()
        val collectedClaim = mockCollectedClaim(user1.id, emailClaim, "test@test.com")

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        coEvery { userRepository.findAll() } returns flowOf(entity1, entity2)
        every { userMapper.toUser(entity1) } returns user1
        every { userMapper.toUser(entity2) } returns user2
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns listOf(claimEntity)
        every { collectedClaimMapper.toCollectedClaim(claimEntity) } returns collectedClaim

        val result = manager.listUsers(
            status = null, query = null, claimFilters = emptyMap(), sort = null, order = null
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `listUsers - filters by status`() = runTest {
        val emailClaim = mockClaim("email")
        val user = mockUser(status = UserStatus.ENABLED)
        val entity = mockk<UserEntity>()

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        coEvery { userRepository.findByStatus("ENABLED") } returns flowOf(entity)
        every { userMapper.toUser(entity) } returns user
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns emptyList()

        val result = manager.listUsers(
            status = "enabled", query = null, claimFilters = emptyMap(), sort = null, order = null
        )

        assertEquals(1, result.size)
        coVerify { userRepository.findByStatus("ENABLED") }
    }

    @Test
    fun `listUsers - throws on invalid status`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()

        val exception = assertThrows<BusinessException> {
            manager.listUsers(
                status = "invalid", query = null, claimFilters = emptyMap(), sort = null, order = null
            )
        }

        assertEquals("user.search.invalid_status", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `listUsers - throws on invalid claim filter`() = runTest {
        val emailClaim = mockClaim("email")
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)

        val exception = assertThrows<BusinessException> {
            manager.listUsers(
                status = null, query = null, claimFilters = mapOf("unknown" to "value"), sort = null, order = null
            )
        }

        assertEquals("user.search.invalid_claim", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `listUsers - throws on invalid sort property`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()

        val exception = assertThrows<BusinessException> {
            manager.listUsers(
                status = null, query = null, claimFilters = emptyMap(), sort = "unknown", order = null
            )
        }

        assertEquals("user.search.invalid_sort", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `listUsers - filters by exact claim value`() = runTest {
        val emailClaim = mockClaim("email")
        val user1 = mockUser()
        val user2 = mockUser()
        val entity1 = mockk<UserEntity>()
        val entity2 = mockk<UserEntity>()
        val claimEntity1 = mockk<CollectedClaimEntity>()
        val claimEntity2 = mockk<CollectedClaimEntity>()
        val cc1 = mockCollectedClaim(user1.id, emailClaim, "jane@example.com")
        val cc2 = mockCollectedClaim(user2.id, emailClaim, "john@example.com")

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        every { claimValueValidator.validateAndCleanValueForClaim(emailClaim, "jane@example.com") } returns Optional.of(
            "jane@example.com"
        )
        coEvery { userRepository.findAll() } returns flowOf(entity1, entity2)
        every { userMapper.toUser(entity1) } returns user1
        every { userMapper.toUser(entity2) } returns user2
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns listOf(claimEntity1, claimEntity2)
        every { collectedClaimMapper.toCollectedClaim(claimEntity1) } returns cc1
        every { collectedClaimMapper.toCollectedClaim(claimEntity2) } returns cc2

        val result = manager.listUsers(
            status = null, query = null, claimFilters = mapOf("email" to "jane@example.com"), sort = null, order = null
        )

        assertEquals(1, result.size)
        assertEquals(user1.id, result.first().user.id)
    }

    @Test
    fun `listUsers - text search across claim values`() = runTest {
        val emailClaim = mockClaim("email")
        val user1 = mockUser()
        val user2 = mockUser()
        val entity1 = mockk<UserEntity>()
        val entity2 = mockk<UserEntity>()
        val claimEntity1 = mockk<CollectedClaimEntity>()
        val claimEntity2 = mockk<CollectedClaimEntity>()
        val cc1 = mockCollectedClaim(user1.id, emailClaim, "jane@example.com")
        val cc2 = mockCollectedClaim(user2.id, emailClaim, "john@example.com")

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        coEvery { userRepository.findAll() } returns flowOf(entity1, entity2)
        every { userMapper.toUser(entity1) } returns user1
        every { userMapper.toUser(entity2) } returns user2
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns listOf(claimEntity1, claimEntity2)
        every { collectedClaimMapper.toCollectedClaim(claimEntity1) } returns cc1
        every { collectedClaimMapper.toCollectedClaim(claimEntity2) } returns cc2

        val result = manager.listUsers(
            status = null, query = "jan", claimFilters = emptyMap(), sort = null, order = null
        )

        assertEquals(1, result.size)
        assertEquals(user1.id, result.first().user.id)
    }

    @Test
    fun `listUsers - sorts by created_at descending`() = runTest {
        val now = LocalDateTime.now()
        val user1 = mockUser(creationDate = now.minusDays(1))
        val user2 = mockUser(creationDate = now)
        val entity1 = mockk<UserEntity>()
        val entity2 = mockk<UserEntity>()

        every { claimManager.listEnabledClaims() } returns emptyList()
        coEvery { userRepository.findAll() } returns flowOf(entity1, entity2)
        every { userMapper.toUser(entity1) } returns user1
        every { userMapper.toUser(entity2) } returns user2
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns emptyList()

        val result = manager.listUsers(
            status = null, query = null, claimFilters = emptyMap(), sort = "created_at", order = "desc"
        )

        assertEquals(user2.id, result.first().user.id)
        assertEquals(user1.id, result.last().user.id)
    }

    @Test
    fun `validateAndResolveClaimIds - returns claims for valid IDs`() {
        val emailClaim = mockClaim("email")
        val nameClaim = mockClaim("name")
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)

        val result = manager.validateAndResolveClaimIds(listOf("email", "name"))

        assertEquals(2, result.size)
    }

    @Test
    fun `validateAndResolveClaimIds - throws for invalid ID`() {
        val emailClaim = mockClaim("email")
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)

        val exception = assertThrows<BusinessException> {
            manager.validateAndResolveClaimIds(listOf("unknown"))
        }

        assertEquals("user.search.invalid_claim", exception.detailsId)
        assertTrue(exception.recoverable)
    }
}

package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.manager.user.UserSearchManager.UserWithClaims
import com.sympauthy.business.model.user.claim.Claim
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
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class UserSearchManagerTest {

    companion object {
        private val NOW: LocalDateTime = LocalDateTime.now()
    }

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var collectedClaimRepository: CollectedClaimRepository

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var generatedClaimsManager: GeneratedClaimsManager

    @MockK
    lateinit var claimValueValidator: ClaimValueValidator

    @MockK
    lateinit var userMapper: UserMapper

    @MockK
    lateinit var collectedClaimMapper: CollectedClaimMapper

    @InjectMockKs
    lateinit var manager: UserSearchManager

    private fun mockClaim(id: String): Claim = mockk {
        every { this@mockk.id } returns id
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

    private fun userWithClaims(user: User, vararg claims: CollectedClaim) = UserWithClaims(
        user = user,
        collectedClaims = claims.toList(),
        generatedClaimValues = emptyMap()
    )

    private val firstPage = PageParams(page = 0, size = 20)

    private fun noGeneratedClaimValue() {
        coEvery { generatedClaimsManager.computeValues(any(), any()) } returns emptyMap()
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
        noGeneratedClaimValue()
        coEvery { userRepository.findAll() } returns flowOf(entity1, entity2)
        every { userMapper.toUser(entity1) } returns user1
        every { userMapper.toUser(entity2) } returns user2
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns listOf(claimEntity)
        every { collectedClaimMapper.toCollectedClaim(claimEntity) } returns collectedClaim

        val result = manager.listUsers(
            status = null, query = null, claimFilters = emptyMap(),
            sort = null, order = null, pageParams = firstPage
        )

        assertEquals(2, result.items.size)
    }

    @Test
    fun `listUsers - filters by status`() = runTest {
        val emailClaim = mockClaim("email")
        val user = mockUser(status = UserStatus.ENABLED)
        val entity = mockk<UserEntity>()

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        coEvery { userRepository.findByStatus("ENABLED") } returns flowOf(entity)
        every { userMapper.toUser(entity) } returns user
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns emptyList()

        val result = manager.listUsers(
            status = "enabled", query = null, claimFilters = emptyMap(),
            sort = null, order = null, pageParams = firstPage
        )

        assertEquals(1, result.items.size)
        coVerify { userRepository.findByStatus("ENABLED") }
    }

    @Test
    fun `listUsers - throws on invalid status`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()

        val exception = assertThrows<BusinessException> {
            manager.listUsers(
                status = "invalid", query = null, claimFilters = emptyMap(),
                sort = null, order = null, pageParams = firstPage
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
                status = null, query = null, claimFilters = mapOf("unknown" to "value"),
                sort = null, order = null, pageParams = firstPage
            )
        }

        assertEquals("user.search.invalid_claim", exception.detailsId)
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
        noGeneratedClaimValue()
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
            status = null, query = null, claimFilters = mapOf("email" to "jane@example.com"),
            sort = null, order = null, pageParams = firstPage
        )

        assertEquals(1, result.items.size)
        assertEquals(user1.id, result.items.first().user.id)
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

        // Only a value collected for a claim that is still enabled is searched.
        every { emailClaim.enabled } returns true
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        coEvery { userRepository.findAll() } returns flowOf(entity1, entity2)
        every { userMapper.toUser(entity1) } returns user1
        every { userMapper.toUser(entity2) } returns user2
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns listOf(claimEntity1, claimEntity2)
        every { collectedClaimMapper.toCollectedClaim(claimEntity1) } returns cc1
        every { collectedClaimMapper.toCollectedClaim(claimEntity2) } returns cc2

        val result = manager.listUsers(
            status = null, query = "jan", claimFilters = emptyMap(),
            sort = null, order = null, pageParams = firstPage
        )

        assertEquals(1, result.items.size)
        assertEquals(user1.id, result.items.first().user.id)
    }

    @Test
    fun `getUserComparator - Throw on a sort property naming nothing`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()

        val exception = assertThrows<BusinessException> {
            manager.getUserComparator(sort = "unknown", order = null)
        }

        assertEquals("user.search.invalid_sort", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `getUserComparator - Order by creation date when no property is named`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()
        val older = userWithClaims(mockUser(creationDate = NOW.minusDays(1)))
        val newer = userWithClaims(mockUser(creationDate = NOW))

        val sorted = listOf(newer, older).sortedWith(manager.getUserComparator(sort = null, order = null))

        assertEquals(listOf(older, newer), sorted)
    }

    @Test
    fun `getUserComparator - Order by creation date, most recent first, under desc`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()
        val older = userWithClaims(mockUser(creationDate = NOW.minusDays(1)))
        val newer = userWithClaims(mockUser(creationDate = NOW))

        val sorted = listOf(older, newer).sortedWith(manager.getUserComparator(sort = "created_at", order = "desc"))

        assertEquals(listOf(newer, older), sorted)
    }

    @Test
    fun `getUserComparator - Order by status`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()
        val disabled = userWithClaims(mockUser(status = UserStatus.DISABLED))
        val enabled = userWithClaims(mockUser(status = UserStatus.ENABLED))

        val sorted = listOf(enabled, disabled).sortedWith(manager.getUserComparator(sort = "status", order = null))

        assertEquals(listOf(disabled, enabled), sorted)
    }

    @Test
    fun `getUserComparator - Order by the value collected for the sort claim`() = runTest {
        val emailClaim = mockClaim("email")
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        val jane = mockUser()
        val john = mockUser()
        val first = userWithClaims(jane, mockCollectedClaim(jane.id, emailClaim, "jane@example.com"))
        val second = userWithClaims(john, mockCollectedClaim(john.id, emailClaim, "john@example.com"))

        val sorted = listOf(second, first).sortedWith(manager.getUserComparator(sort = "email", order = null))

        assertEquals(listOf(first, second), sorted)
    }

    @Test
    fun `getUserComparator - Order a user who collected no value for the sort claim last`() = runTest {
        val emailClaim = mockClaim("email")
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        val jane = mockUser()
        val withValue = userWithClaims(jane, mockCollectedClaim(jane.id, emailClaim, "jane@example.com"))
        val withoutValue = userWithClaims(mockUser())

        val sorted = listOf(withoutValue, withValue).sortedWith(manager.getUserComparator(sort = "email", order = null))

        assertEquals(listOf(withValue, withoutValue), sorted)
    }

    @Test
    fun `getUserComparator - Break a tie on the creation date with the user identifier`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()
        val one = userWithClaims(mockUser(creationDate = NOW))
        val other = userWithClaims(mockUser(creationDate = NOW))

        assertOrderedById(one, other, manager.getUserComparator(sort = "created_at", order = null))
    }

    @Test
    fun `getUserComparator - Break a tie on the status with the user identifier`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()
        val one = userWithClaims(mockUser(status = UserStatus.ENABLED))
        val other = userWithClaims(mockUser(status = UserStatus.ENABLED))

        assertOrderedById(one, other, manager.getUserComparator(sort = "status", order = null))
    }

    @Test
    fun `getUserComparator - Break a tie on the claim value with the user identifier`() = runTest {
        val emailClaim = mockClaim("email")
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        val one = mockUser()
        val other = mockUser()

        assertOrderedById(
            userWithClaims(one, mockCollectedClaim(one.id, emailClaim, "shared@example.com")),
            userWithClaims(other, mockCollectedClaim(other.id, emailClaim, "shared@example.com")),
            manager.getUserComparator(sort = "email", order = null)
        )
    }

    @Test
    fun `getUserComparator - Keep the identifier tiebreak ascending under desc`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()
        val one = userWithClaims(mockUser(creationDate = NOW))
        val other = userWithClaims(mockUser(creationDate = NOW))

        assertOrderedById(one, other, manager.getUserComparator(sort = "created_at", order = "desc"))
    }

    /**
     * Assert that [comparator] puts the two tied users in ascending identifier order whichever order it is
     * handed them in, which is what makes the order total rather than merely stable.
     */
    private fun assertOrderedById(
        one: UserWithClaims,
        other: UserWithClaims,
        comparator: Comparator<UserWithClaims>
    ) {
        val expected = listOf(one, other).sortedBy { it.user.id }

        assertEquals(expected, listOf(one, other).sortedWith(comparator))
        assertEquals(expected, listOf(other, one).sortedWith(comparator))
    }

    @Test
    fun `listUsers - Answer each user with the values generated for them`() = runTest {
        val emailClaim = mockClaim("email")
        val user = mockUser()
        val entity = mockk<UserEntity>()
        val claimEntity = mockk<CollectedClaimEntity>()
        val collectedClaim = mockCollectedClaim(user.id, emailClaim, "jane@example.com")

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        coEvery { userRepository.findAll() } returns flowOf(entity)
        every { userMapper.toUser(entity) } returns user
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns listOf(claimEntity)
        every { collectedClaimMapper.toCollectedClaim(claimEntity) } returns collectedClaim
        coEvery {
            generatedClaimsManager.computeValues(user.id, listOf(collectedClaim))
        } returns mapOf("sub" to user.id.toString())

        val result = manager.listUsers(
            status = null, query = null, claimFilters = emptyMap(),
            sort = null, order = null, pageParams = firstPage
        )

        assertEquals(mapOf("sub" to user.id.toString()), result.items.single().generatedClaimValues)
    }

    @Test
    fun `listUsers - Refuse a sort property naming nothing before reading a user`() = runTest {
        every { claimManager.listEnabledClaims() } returns emptyList()

        val exception = assertThrows<BusinessException> {
            manager.listUsers(
                status = null, query = null, claimFilters = emptyMap(),
                sort = "nope", order = null, pageParams = firstPage
            )
        }

        assertEquals("user.search.invalid_sort", exception.detailsId)
    }

    @Test
    fun `listUsers - Return the page the parameters name, out of everything the criteria kept`() = runTest {
        val emailClaim = mockClaim("email")
        val entity1 = mockk<UserEntity>()
        val entity2 = mockk<UserEntity>()

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        coEvery { userRepository.findAll() } returns flowOf(entity1, entity2)
        every { userMapper.toUser(entity1) } returns mockUser()
        every { userMapper.toUser(entity2) } returns mockUser()
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns emptyList()

        val result = manager.listUsers(
            status = null, query = null, claimFilters = emptyMap(),
            sort = null, order = null, pageParams = PageParams(0, 1)
        )

        assertEquals(1, result.items.size)
        assertEquals(0, result.page)
        assertEquals(1, result.size)
        assertEquals(2, result.total)
    }

    @Test
    fun `listSelectedClaims - Select every enabled claim when the caller named none`() = runTest {
        val enabledClaims = listOf(mockk<Claim>())
        every { claimManager.listEnabledClaims() } returns enabledClaims

        val result = manager.listSelectedClaims(null)

        assertEquals(enabledClaims, result)
    }

    @Test
    fun `listSelectedClaims - Select no claim when the caller named an empty list`() = runTest {
        assertNull(manager.listSelectedClaims(emptyList()))
    }

    @Test
    fun `listSelectedClaims - Select the claims the caller named`() = runTest {
        val emailClaim = mockClaim("email")
        val nameClaim = mockClaim("name")
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)

        val result = manager.listSelectedClaims(listOf("name"))

        assertEquals(listOf(nameClaim), result)
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

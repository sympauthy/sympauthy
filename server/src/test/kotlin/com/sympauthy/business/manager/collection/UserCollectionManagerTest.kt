package com.sympauthy.business.manager.collection

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.mapper.CollectedClaimMapper
import com.sympauthy.business.mapper.UserMapper
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.api.util.criteriaOf
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
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
class UserCollectionManagerTest {

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
    lateinit var userMapper: UserMapper

    @MockK
    lateinit var collectedClaimMapper: CollectedClaimMapper

    @InjectMockKs
    lateinit var manager: UserCollectionManager

    private val acl = ClaimAcl(
        consent = ConsentAcl(
            scope = null,
            readableByUser = false,
            writableByUser = false,
            readableByClient = false,
            writableByClient = false
        ),
        unconditional = UnconditionalAcl(emptyList(), emptyList())
    )

    private fun claim(
        id: String,
        dataType: ClaimDataType = ClaimDataType.STRING,
        generated: Boolean = false
    ) = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = dataType,
        group = null,
        required = false,
        generated = generated,
        userInputted = false,
        allowedValues = null,
        acl = acl
    )

    private val emailClaim = claim("email", dataType = ClaimDataType.EMAIL)

    private fun mockUser(
        status: UserStatus = UserStatus.ENABLED,
        creationDate: LocalDateTime = LocalDateTime.now()
    ) = User(
        id = UUID.randomUUID(),
        status = status,
        creationDate = creationDate,
        sessionId = null
    )

    private val firstPage = PageParams(page = 0, size = 20)

    private fun noGeneratedClaimValue() {
        coEvery { generatedClaimsManager.computeValues(any(), any()) } returns emptyMap()
    }

    private fun claimEntity(userId: UUID, collectionDate: LocalDateTime = LocalDateTime.now()) =
        CollectedClaimEntity(
            userId = userId,
            claim = "email",
            value = null,
            verified = null,
            collectionDate = collectionDate,
            verificationDate = null,
            sessionId = null
        )

    private fun mockCollectedClaim(userId: UUID, claim: Claim, value: Any?) = CollectedClaim(
        userId = userId,
        claim = claim,
        value = value,
        verified = null,
        collectionDate = LocalDateTime.now(),
        verificationDate = null
    )

    /**
     * Read the whole table as [users], each with the claim values [claims] holds for them.
     */
    private fun givenUsers(users: List<User>, claims: Map<User, String> = emptyMap()) {
        val entities = users.associateWith { mockk<UserEntity>() }
        coEvery { userRepository.findBySessionIdIsNull() } returns flowOf(*entities.values.toTypedArray())
        entities.forEach { (user, entity) -> every { userMapper.toUser(entity) } returns user }

        val claimEntities = claims.keys.associateWith { claimEntity(it.id) }
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns claimEntities.values.toList()
        claimEntities.forEach { (user, entity) ->
            every { collectedClaimMapper.toCollectedClaim(entity) } returns
                    mockCollectedClaim(user.id, emailClaim, claims.getValue(user))
        }
    }

    private suspend fun criteriaOf(
        vararg filters: Pair<String, String>,
        sort: String? = null,
        query: String? = null
    ): CollectionCriteria = manager.capabilities().criteriaOf(*filters, sort = sort, query = query)

    @Test
    fun `listUsers - Keep every user when the criteria name nothing`() = runTest {
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(mockUser(), mockUser()))

        val result = manager.listUsers(criteriaOf(), firstPage)

        assertEquals(2, result.items.size)
    }

    @Test
    fun `listUsers - Keep the users of the status the criterion names`() = runTest {
        val enabled = mockUser(status = UserStatus.ENABLED)
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(enabled, mockUser(status = UserStatus.DISABLED)))

        val result = manager.listUsers(criteriaOf("status" to "enabled"), firstPage)

        assertEquals(listOf(enabled.id), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - Keep the users holding the claim value a criterion names`() = runTest {
        val jane = mockUser()
        val john = mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(jane, john), mapOf(jane to "jane@example.com", john to "john@example.com"))

        val result = manager.listUsers(criteriaOf("email" to "jane@example.com"), firstPage)

        assertEquals(listOf(jane.id), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - Keep the users a fragment of a claim value names`() = runTest {
        val jane = mockUser()
        val john = mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(jane, john), mapOf(jane to "jane@example.com", john to "john@example.com"))

        val result = manager.listUsers(criteriaOf("email.contains" to "JANE@"), firstPage)

        assertEquals(listOf(jane.id), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - Keep the users a free text search matches against a claim value`() = runTest {
        val jane = mockUser()
        val john = mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(jane, john), mapOf(jane to "jane@example.com", john to "john@example.com"))

        val result = manager.listUsers(criteriaOf(query = "jan"), firstPage)

        assertEquals(listOf(jane.id), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - Offer a field per claim this deployment collects`() = runTest {
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, claim("nickname"))

        val fields = manager.capabilities().filterFields.map { it.name }

        assertTrue(fields.containsAll(listOf("email", "nickname")))
    }

    @Test
    fun `listUsers - Offer no field for a claim this server computes`() = runTest {
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, claim("sub", generated = true))

        val fields = manager.capabilities().fields.map { it.name }

        assertEquals(listOf("id", "status", "created_at", "email"), fields)
    }

    @Test
    fun `listUsers - Offer no field for a claim named as a parameter of the grammar`() = runTest {
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, claim("q"), claim("sort"))

        val fields = manager.capabilities().fields.map { it.name }

        // A field under one of those names is one no criterion could reach: the resolver reads them
        // for the free text and the order before it reads anything as a criterion.
        assertEquals(listOf("id", "status", "created_at", "email"), fields)
    }

    @Test
    fun `listUsers - Offer the account's own field rather than the claim that shares its name`() = runTest {
        every { claimManager.listEnabledClaims() } returns listOf(claim("status"))

        val status = manager.capabilities().fields.single { it.name == "status" }

        assertEquals("fields.user_status", status.key)
    }

    @Test
    fun `listUsers - Order by creation date, oldest first, when the caller names no key`() = runTest {
        val older = mockUser(creationDate = NOW.minusDays(1))
        val newer = mockUser(creationDate = NOW)
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(newer, older))

        val result = manager.listUsers(criteriaOf(), firstPage)

        assertEquals(listOf(older.id, newer.id), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - Order by the key the caller named, reversed under a leading dash`() = runTest {
        val older = mockUser(creationDate = NOW.minusDays(1))
        val newer = mockUser(creationDate = NOW)
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(older, newer))

        val result = manager.listUsers(criteriaOf(sort = "-created_at"), firstPage)

        assertEquals(listOf(newer.id, older.id), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - Order by the value collected for the claim the key names`() = runTest {
        val jane = mockUser()
        val john = mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(john, jane), mapOf(jane to "jane@example.com", john to "john@example.com"))

        val result = manager.listUsers(criteriaOf(sort = "email"), firstPage)

        assertEquals(listOf(jane.id, john.id), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - Order a user holding no value for the key last`() = runTest {
        val withValue = mockUser()
        val withoutValue = mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(withoutValue, withValue), mapOf(withValue to "jane@example.com"))

        val result = manager.listUsers(criteriaOf(sort = "email"), firstPage)

        assertEquals(listOf(withValue.id, withoutValue.id), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - End the order on the user identifier, ascending under a leading dash`() = runTest {
        val one = mockUser(creationDate = NOW)
        val other = mockUser(creationDate = NOW)
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(other, one))

        val result = manager.listUsers(criteriaOf(sort = "-created_at"), firstPage)

        assertEquals(listOf(one, other).map { it.id }.sorted(), result.items.map { it.user.id })
    }

    @Test
    fun `listUsers - Answer each user with the values generated for them`() = runTest {
        val user = mockUser()
        val collectedAt = LocalDateTime.of(2025, 6, 1, 0, 0)
        val entity = mockk<UserEntity>()
        val claimEntity = claimEntity(user.id, collectedAt)

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        coEvery { userRepository.findBySessionIdIsNull() } returns flowOf(entity)
        every { userMapper.toUser(entity) } returns user
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns listOf(claimEntity)
        every { collectedClaimMapper.toCollectedClaim(claimEntity) } returns
                mockCollectedClaim(user.id, emailClaim, "jane@example.com")
        coEvery {
            generatedClaimsManager.computeValues(user.id, collectedAt)
        } returns mapOf("sub" to user.id.toString())

        val result = manager.listUsers(criteriaOf(), firstPage)

        assertEquals(mapOf("sub" to user.id.toString()), result.items.single().generatedClaimValues)
    }

    @Test
    fun `listUsers - Compute the values generated for the users of the page and for no other`() = runTest {
        val onPage = mockUser(creationDate = NOW.minusDays(1))
        val offPage = mockUser(creationDate = NOW)
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        givenUsers(listOf(onPage, offPage))
        coEvery {
            generatedClaimsManager.computeValues(onPage.id, null)
        } returns mapOf("sub" to onPage.id.toString())

        val result = manager.listUsers(criteriaOf(), PageParams(0, 1))

        assertEquals(mapOf("sub" to onPage.id.toString()), result.items.single().generatedClaimValues)
        coVerify(exactly = 0) { generatedClaimsManager.computeValues(offPage.id, any()) }
    }

    @Test
    fun `listUsers - Compute no generated value for a user the criteria dropped`() = runTest {
        val kept = mockUser()
        val dropped = mockUser()
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        givenUsers(listOf(kept, dropped), mapOf(kept to "jane@example.com", dropped to "john@example.com"))
        coEvery { generatedClaimsManager.computeValues(kept.id, any()) } returns emptyMap()

        val result = manager.listUsers(criteriaOf(query = "jan"), firstPage)

        assertEquals(kept.id, result.items.single().user.id)
        coVerify(exactly = 0) { generatedClaimsManager.computeValues(dropped.id, any()) }
    }

    @Test
    fun `listUsers - Return the page the parameters name, out of everything the criteria kept`() = runTest {
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        noGeneratedClaimValue()
        givenUsers(listOf(mockUser(), mockUser()))

        val result = manager.listUsers(criteriaOf(), PageParams(0, 1))

        assertEquals(1, result.items.size)
        assertEquals(0, result.page)
        assertEquals(1, result.size)
        assertEquals(2, result.total)
    }

    @Test
    fun `listUsers - Date the last update from a row whose claim the configuration dropped`() = runTest {
        val user = mockUser()
        val collectedAt = LocalDateTime.of(2025, 6, 1, 0, 0)
        val droppedAt = collectedAt.plusDays(1)
        val entity = mockk<UserEntity>()
        val mapped = claimEntity(user.id, collectedAt)
        // The claim this row carries is no longer configured, so it never becomes a model — and it
        // is still the last thing collected from the user.
        val unmappable = claimEntity(user.id, droppedAt)

        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)
        coEvery { userRepository.findBySessionIdIsNull() } returns flowOf(entity)
        every { userMapper.toUser(entity) } returns user
        coEvery { collectedClaimRepository.findByUserIdInList(any()) } returns listOf(mapped, unmappable)
        every { collectedClaimMapper.toCollectedClaim(mapped) } returns
                mockCollectedClaim(user.id, emailClaim, "jane@example.com")
        every { collectedClaimMapper.toCollectedClaim(unmappable) } returns null
        coEvery { generatedClaimsManager.computeValues(user.id, droppedAt) } returns emptyMap()

        val result = manager.listUsers(criteriaOf(), firstPage)

        assertEquals(1, result.items.size)
    }

    @Test
    fun `listSelectedClaims - Select every enabled claim when the caller named none`() = runTest {
        val enabledClaims = listOf(emailClaim)
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
        val nameClaim = claim("name")
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, nameClaim)

        val result = manager.listSelectedClaims(listOf("name"))

        assertEquals(listOf(nameClaim), result)
    }

    @Test
    fun `validateAndResolveClaimIds - returns claims for valid IDs`() {
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim, claim("name"))

        val result = manager.validateAndResolveClaimIds(listOf("email", "name"))

        assertEquals(2, result.size)
    }

    @Test
    fun `validateAndResolveClaimIds - throws for invalid ID`() {
        every { claimManager.listEnabledClaims() } returns listOf(emailClaim)

        val exception = assertThrows<BusinessException> {
            manager.validateAndResolveClaimIds(listOf("unknown"))
        }

        assertEquals("user.claims.unknown", exception.detailsId)
        assertTrue(exception.recoverable)
    }
}

package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserDetailResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.collection.UserCollectionManager
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldType
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.enumFieldValues
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.manager.collection.UserCollectionManager.UserWithClaims
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminUserControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var userCollectionManager: UserCollectionManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var userMapper: AdminUserResourceMapper

    @MockK
    lateinit var userDetailMapper: AdminUserDetailResourceMapper

    @MockK
    lateinit var capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminUserController

    private val userId: UUID = UUID.randomUUID()
    private val creationDate: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

    private val defaultPage = PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)

    private fun pageOf(vararg users: UserWithClaims) = Page(
        items = users.toList(),
        page = DEFAULT_PAGE,
        size = TEST_DEFAULT_PAGE_SIZE,
        total = users.size
    )

    private val statusField = CollectionField(
        name = "status",
        type = CollectionFieldType.ENUM,
        key = "fields.user_status",
        operators = setOf(CollectionOperator.EQ),
        values = enumFieldValues<UserStatus>("fields.user_status"),
        sortable = true
    )

    private val emailField = CollectionField(
        name = "email",
        type = CollectionFieldType.EMAIL,
        key = "claims.email",
        operators = setOf(CollectionOperator.EQ, CollectionOperator.CONTAINS),
        sortable = true,
        searchable = true
    )

    private val capabilities = CollectionCapabilities(listOf(statusField, emailField), emptyList())

    private fun userWithClaims(creationDate: LocalDateTime) = UserWithClaims(
        user = User(
            id = UUID.randomUUID(),
            status = UserStatus.ENABLED,
            creationDate = creationDate,
            sessionId = null
        ),
        collectedClaims = emptyList(),
        generatedClaimValues = emptyMap()
    )

    @Test
    fun `listUsers - Map every user the page holds, in the order it holds them`() = runTest {
        val first = userWithClaims(creationDate)
        val second = userWithClaims(creationDate.plusDays(1))
        val firstResource = AdminUserResource(
            userId = first.user.id,
            status = "enabled",
            createdAt = creationDate,
            claims = null
        )
        val secondResource = AdminUserResource(
            userId = second.user.id,
            status = "enabled",
            createdAt = creationDate.plusDays(1),
            claims = null
        )

        coEvery { userCollectionManager.capabilities() } returns capabilities
        coEvery { userCollectionManager.listSelectedClaims(null) } returns emptyList()
        coEvery { userCollectionManager.listUsers(any(), defaultPage) } returns pageOf(first, second)
        every { userMapper.toResource(first, emptyList()) } returns firstResource
        every { userMapper.toResource(second, emptyList()) } returns secondResource

        val result = controller.listUsers(collectionRequest(), null, null, null, null, null)

        assertEquals(listOf(first.user.id, second.user.id), result.users.map { it.userId })
    }

    @Test
    fun `listUsers - Hand the manager every criterion, the order and the page the request names`() = runTest {
        val user = userWithClaims(creationDate)
        val resource = AdminUserResource(
            userId = user.user.id,
            status = "enabled",
            createdAt = creationDate,
            claims = null
        )
        val criteria = slot<CollectionCriteria>()

        coEvery { userCollectionManager.capabilities() } returns capabilities
        coEvery { userCollectionManager.listSelectedClaims(null) } returns emptyList()
        coEvery { userCollectionManager.listUsers(capture(criteria), PageParams(1, 2)) } returns pageOf(user)
        every { userMapper.toResource(user, emptyList()) } returns resource

        val result = controller.listUsers(
            collectionRequest("status=enabled&email.contains=ana"), 1, 2, null, "-email", "jane"
        )

        assertSame(resource, result.users.single())
        assertEquals(
            listOf("status" to CollectionOperator.EQ, "email" to CollectionOperator.CONTAINS),
            criteria.captured.filters.map { it.field.name to it.operator }
        )
        assertEquals("-email", criteria.captured.sort.single().spelling)
        assertEquals("jane", criteria.captured.query)
    }

    @Test
    fun `listUsers - Keep the claims parameter out of the criteria it selects nothing with`() = runTest {
        coEvery { userCollectionManager.capabilities() } returns capabilities
        coEvery { userCollectionManager.listSelectedClaims(listOf("email")) } returns emptyList()
        coEvery { userCollectionManager.listUsers(CollectionCriteria.NONE, defaultPage) } returns pageOf()

        val result = controller.listUsers(collectionRequest("claims=email"), null, null, "email", null, null)

        assertEquals(0, result.total)
    }

    @Test
    fun `listUsers - Select the claims the parameter names`() = runTest {
        val user = userWithClaims(creationDate)
        val resource = AdminUserResource(
            userId = user.user.id,
            status = "enabled",
            createdAt = creationDate,
            claims = null
        )

        coEvery { userCollectionManager.capabilities() } returns capabilities
        coEvery { userCollectionManager.listSelectedClaims(listOf("email", "name")) } returns emptyList()
        coEvery { userCollectionManager.listUsers(any(), defaultPage) } returns pageOf(user)
        every { userMapper.toResource(user, emptyList()) } returns resource

        val result = controller.listUsers(collectionRequest(), null, null, " email , name ", null, null)

        assertEquals(listOf(user.user.id), result.users.map { it.userId })
    }

    @Test
    fun `listUsers - Select no claim where the parameter is empty`() = runTest {
        val user = userWithClaims(creationDate)
        val resource = AdminUserResource(
            userId = user.user.id,
            status = "enabled",
            createdAt = creationDate,
            claims = null
        )

        coEvery { userCollectionManager.capabilities() } returns capabilities
        coEvery { userCollectionManager.listSelectedClaims(emptyList()) } returns null
        coEvery { userCollectionManager.listUsers(any(), defaultPage) } returns pageOf(user)
        every { userMapper.toResource(user, null) } returns resource

        val result = controller.listUsers(collectionRequest(), null, null, "", null, null)

        assertEquals(listOf(user.user.id), result.users.map { it.userId })
    }

    @Test
    fun `listUsers - Publish the page the manager answered, not the one that was asked for`() = runTest {
        coEvery { userCollectionManager.capabilities() } returns capabilities
        coEvery { userCollectionManager.listSelectedClaims(null) } returns emptyList()
        coEvery { userCollectionManager.listUsers(any(), defaultPage) } returns
                Page(items = emptyList(), page = 3, size = 7, total = 42)

        val result = controller.listUsers(collectionRequest(), null, null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }

    @Test
    fun `listUsers - Refuse a status the set does not hold`() = runTest {
        // The manager is not stubbed on purpose: reaching the assertion is proof that a status naming
        // nothing is refused before anything is read.
        coEvery { userCollectionManager.capabilities() } returns capabilities

        val exception = assertThrows<LocalizedHttpException> {
            controller.listUsers(collectionRequest("status=disabl"), null, null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listUsers - Refuse an operator the field does not admit`() = runTest {
        coEvery { userCollectionManager.capabilities() } returns capabilities

        val exception = assertThrows<LocalizedHttpException> {
            controller.listUsers(collectionRequest("status.contains=ena"), null, null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.unsupported_operator", exception.detailsId)
    }

    @Test
    fun `listUsers - Refuse a sort key this collection is not ordered by`() = runTest {
        // Unstubbed for the same reason: a key naming nothing is refused before anything is read,
        // rather than sorting the page by something else in silence.
        coEvery { userCollectionManager.capabilities() } returns capabilities

        val exception = assertThrows<LocalizedHttpException> {
            controller.listUsers(collectionRequest(), null, null, null, "surname", null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.sort.unknown_field", exception.detailsId)
    }

    @Test
    fun `getUser - Returns user with identifier claims`() = runTest {
        val user = User(
            id = userId,
            status = UserStatus.ENABLED,
            creationDate = creationDate,
            sessionId = null
        )
        val identifierClaims = listOf(mockk<CollectedClaim>())
        val identifierClaimsMap = mapOf("email" to "user@example.com")

        val expectedResource = AdminUserDetailResource(
            userId = userId,
            status = "enabled",
            createdAt = creationDate,
            identifierClaims = identifierClaimsMap
        )

        coEvery { userManager.findByIdOrNull(userId) } returns user
        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns identifierClaims
        every { userDetailMapper.toResource(user, identifierClaims) } returns expectedResource

        val result = controller.getUser(userId)

        assertEquals(userId, result.userId)
        assertEquals("enabled", result.status)
        assertEquals(identifierClaimsMap, result.identifierClaims)
    }

    @Test
    fun `getUser - Returns 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.getUser(userId)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}

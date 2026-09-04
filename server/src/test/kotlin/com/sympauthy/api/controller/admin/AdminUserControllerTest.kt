package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminUserDetailResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.user.UserSearchManager
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.manager.user.UserSearchManager.UserWithClaims
import io.micronaut.http.HttpParameters
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
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
    lateinit var userSearchManager: UserSearchManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var userMapper: AdminUserResourceMapper

    @MockK
    lateinit var userDetailMapper: AdminUserDetailResourceMapper

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

    private fun requestWithoutClaimFilter(): HttpRequest<*> {
        val parameters = mockk<HttpParameters> {
            every { asMap() } returns emptyMap()
        }
        return mockk {
            every { this@mockk.parameters } returns parameters
        }
    }

    private fun userWithClaims(creationDate: LocalDateTime) = UserWithClaims(
        user = User(id = UUID.randomUUID(), status = UserStatus.ENABLED, creationDate = creationDate),
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

        coEvery { userSearchManager.listSelectedClaims(null) } returns emptyList()
        coEvery {
            userSearchManager.listUsers(null, null, emptyMap(), null, null, defaultPage)
        } returns pageOf(first, second)
        every { userMapper.toResource(first, emptyList()) } returns firstResource
        every { userMapper.toResource(second, emptyList()) } returns secondResource

        val result = controller.listUsers(requestWithoutClaimFilter(), null, null, null, null, null, null, null)

        assertEquals(listOf(first.user.id, second.user.id), result.users.map { it.userId })
    }

    @Test
    fun `listUsers - Ask the search for the order and the page the parameters name`() = runTest {
        val user = userWithClaims(creationDate)
        val resource = AdminUserResource(
            userId = user.user.id,
            status = "enabled",
            createdAt = creationDate,
            claims = null
        )

        coEvery { userSearchManager.listSelectedClaims(null) } returns emptyList()
        coEvery {
            userSearchManager.listUsers(UserStatus.ENABLED, "jane", emptyMap(), "email", "desc", PageParams(1, 2))
        } returns pageOf(user)
        every { userMapper.toResource(user, emptyList()) } returns resource

        val result = controller.listUsers(
            requestWithoutClaimFilter(), 1, 2, "enabled", null, "jane", "email", "desc"
        )

        assertSame(resource, result.users.single())
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

        coEvery { userSearchManager.listSelectedClaims(listOf("email", "name")) } returns emptyList()
        coEvery {
            userSearchManager.listUsers(null, null, emptyMap(), null, null, defaultPage)
        } returns pageOf(user)
        every { userMapper.toResource(user, emptyList()) } returns resource

        val result = controller.listUsers(
            requestWithoutClaimFilter(), null, null, null, " email , name ", null, null, null
        )

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

        coEvery { userSearchManager.listSelectedClaims(emptyList()) } returns null
        coEvery {
            userSearchManager.listUsers(null, null, emptyMap(), null, null, defaultPage)
        } returns pageOf(user)
        every { userMapper.toResource(user, null) } returns resource

        val result = controller.listUsers(requestWithoutClaimFilter(), null, null, null, "", null, null, null)

        assertEquals(listOf(user.user.id), result.users.map { it.userId })
    }

    @Test
    fun `listUsers - Publish the page the search answered, not the one that was asked for`() = runTest {
        coEvery { userSearchManager.listSelectedClaims(null) } returns emptyList()
        coEvery {
            userSearchManager.listUsers(null, null, emptyMap(), null, null, defaultPage)
        } returns Page(items = emptyList(), page = 3, size = 7, total = 42)

        val result = controller.listUsers(requestWithoutClaimFilter(), null, null, null, null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }

    @Test
    fun `listUsers - Refuse a status the set does not hold`() = runTest {
        // Neither the request nor the search is stubbed on purpose: reaching the assertion is proof
        // that a status naming nothing is refused before either of them is read.
        val exception = assertThrows<LocalizedHttpException> {
            controller.listUsers(mockk(), null, null, "disabl", null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `getUser - Returns user with identifier claims`() = runTest {
        val user = User(id = userId, status = UserStatus.ENABLED, creationDate = creationDate)
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

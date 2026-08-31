package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminUserDetailResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.api.resource.admin.AdminUserResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.user.UserSearchManager
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.UserWithClaims
import io.micronaut.http.HttpParameters
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminUserControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var userSearchManager: UserSearchManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK(relaxed = true)
    lateinit var generatedClaimsManager: GeneratedClaimsManager

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

    private fun userWithClaims(creationDate: LocalDateTime) = UserWithClaims(
        user = User(id = UUID.randomUUID(), status = UserStatus.ENABLED, creationDate = creationDate),
        collectedClaims = emptyList()
    )

    @Test
    fun `listUsers - Order with the comparator the search builds, then slice`() = runTest {
        val older = userWithClaims(creationDate)
        val newer = userWithClaims(creationDate.plusDays(1))
        val parameters = mockk<HttpParameters> {
            every { asMap() } returns emptyMap()
        }
        val request = mockk<HttpRequest<*>> {
            every { this@mockk.parameters } returns parameters
        }
        val olderResource = AdminUserResource(
            userId = older.user.id,
            status = "enabled",
            createdAt = creationDate,
            claims = null
        )

        every { claimManager.listEnabledClaims() } returns emptyList()
        coEvery {
            userSearchManager.getUserComparator(null, null)
        } returns compareBy<UserWithClaims> { it.user.creationDate }
        // Handed newest first, the first page of one still holds the user the comparator puts first.
        coEvery { userSearchManager.listUsers(null, null, emptyMap()) } returns listOf(newer, older)
        every { userMapper.buildClaimsMap(emptyList(), emptyList(), any()) } returns null
        every { userMapper.toResource(older.user, null) } returns olderResource

        val result = controller.listUsers(request, 0, 1, null, null, null, null, null)

        assertEquals(listOf(older.user.id), result.users.map { it.userId })
        assertEquals(2, result.total)
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

package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminUserDetailResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.GeneratedClaimsManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.user.UserSearchManager
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
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
@MockKExtension.CheckUnnecessaryStub
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

    @InjectMockKs
    lateinit var controller: AdminUserController

    private val userId: UUID = UUID.randomUUID()
    private val creationDate: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

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

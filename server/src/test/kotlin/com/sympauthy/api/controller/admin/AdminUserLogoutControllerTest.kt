package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.user.User
import com.sympauthy.security.UserAuthentication
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminUserLogoutControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var grantManager: GrantManager

    @InjectMockKs
    lateinit var controller: AdminUserLogoutController

    private val userId: UUID = UUID.randomUUID()
    private val user = mockk<User>()
    private val client = mockk<Client>()
    private val adminId: UUID = UUID.randomUUID()
    private val clientId: String = "my-app"

    private fun mockAdminAuthentication(): UserAuthentication = mockk {
        every { authenticationToken } returns mockk<AuthenticationToken> token@{
            every { grant } returns mockk<Grant> grant@{
                every { this@grant.userId } returns adminId
            }
        }
    }

    @Test
    fun `forceLogout - Revokes all user grants and returns count`() = runTest {
        val authentication = mockAdminAuthentication()
        coEvery { userManager.findByIdOrNull(userId) } returns user
        coEvery { grantManager.revokeGrantsByUser(user, GrantRevokedBy.ADMIN, adminId) } returns 5

        val result = controller.forceLogout(userId, authentication)

        assertEquals(userId, result.userId)
        assertNull(result.clientId)
        assertEquals(5, result.grantsRevoked)
    }

    @Test
    fun `forceLogout - Returns 404 when user not found`() = runTest {
        val authentication = mockk<UserAuthentication>()
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.forceLogout(userId, authentication)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `forceClientLogout - Revokes all grants for user+client and returns count`() = runTest {
        val authentication = mockAdminAuthentication()
        coEvery { userManager.findByIdOrNull(userId) } returns user
        coEvery { clientManager.findClientByIdOrNull(clientId) } returns client
        coEvery { grantManager.revokeGrantsByUserAndClient(user, client, GrantRevokedBy.ADMIN, adminId) } returns 3

        val result = controller.forceClientLogout(userId, clientId, authentication)

        assertEquals(userId, result.userId)
        assertEquals(clientId, result.clientId)
        assertEquals(3, result.grantsRevoked)
    }

    @Test
    fun `forceClientLogout - Returns 404 when user not found`() = runTest {
        val authentication = mockk<UserAuthentication>()
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.forceClientLogout(userId, clientId, authentication)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `forceClientLogout - Returns 404 when client not found`() = runTest {
        val authentication = mockk<UserAuthentication>()
        coEvery { userManager.findByIdOrNull(userId) } returns user
        coEvery { clientManager.findClientByIdOrNull(clientId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.forceClientLogout(userId, clientId, authentication)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}

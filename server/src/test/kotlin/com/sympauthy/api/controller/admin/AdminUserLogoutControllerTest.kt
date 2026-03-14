package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.oauth2.TokenManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.TokenRevokedBy
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
    lateinit var tokenManager: TokenManager

    @InjectMockKs
    lateinit var controller: AdminUserLogoutController

    private val userId: UUID = UUID.randomUUID()
    private val adminId: UUID = UUID.randomUUID()
    private val clientId: String = "my-app"

    private fun mockAuthentication(): UserAuthentication = mockk {
        every { authenticationToken } returns mockk<AuthenticationToken> {
            every { this@mockk.userId } returns adminId
        }
    }

    @Test
    fun `forceLogout - Revokes all user tokens and returns count`() = runTest {
        val authentication = mockAuthentication()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { tokenManager.revokeTokensByUser(userId, TokenRevokedBy.ADMIN, adminId) } returns 5

        val result = controller.forceLogout(userId, authentication)

        assertEquals(userId, result.userId)
        assertNull(result.clientId)
        assertEquals(5, result.tokensRevoked)
    }

    @Test
    fun `forceLogout - Returns 404 when user not found`() = runTest {
        val authentication = mockAuthentication()
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.forceLogout(userId, authentication)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `forceClientLogout - Revokes all tokens for user+client and returns count`() = runTest {
        val authentication = mockAuthentication()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { clientManager.findClientByIdOrNull(clientId) } returns mockk<Client>()
        coEvery { tokenManager.revokeTokensByUserAndClient(userId, clientId, TokenRevokedBy.ADMIN, adminId) } returns 3

        val result = controller.forceClientLogout(userId, clientId, authentication)

        assertEquals(userId, result.userId)
        assertEquals(clientId, result.clientId)
        assertEquals(3, result.tokensRevoked)
    }

    @Test
    fun `forceClientLogout - Returns 404 when user not found`() = runTest {
        val authentication = mockAuthentication()
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.forceClientLogout(userId, clientId, authentication)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `forceClientLogout - Returns 404 when client not found`() = runTest {
        val authentication = mockAuthentication()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { clientManager.findClientByIdOrNull(clientId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.forceClientLogout(userId, clientId, authentication)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}

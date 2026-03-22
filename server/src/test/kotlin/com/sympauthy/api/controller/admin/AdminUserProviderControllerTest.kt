package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminUserProviderControllerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @InjectMockKs
    lateinit var controller: AdminUserProviderController

    private val userId: UUID = UUID.randomUUID()
    private val linkedAt: LocalDateTime = LocalDateTime.of(2026, 1, 15, 14, 30, 0)

    private fun mockProviderUserInfo(
        providerId: String = "discord",
        subject: String = "123456789012345678"
    ): ProviderUserInfo = ProviderUserInfo(
        providerId = providerId,
        userId = userId,
        fetchDate = linkedAt,
        changeDate = linkedAt,
        userInfo = RawProviderClaims(subject = subject)
    )

    @Test
    fun `listProviders - Returns paginated list of linked providers`() = runTest {
        val providerInfo = mockProviderUserInfo()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { providerClaimsManager.findByUserId(userId) } returns listOf(providerInfo)

        val result = controller.listProviders(userId, null, null)

        assertEquals(1, result.providers.size)
        assertEquals("discord", result.providers[0].providerId)
        assertEquals("123456789012345678", result.providers[0].subject)
        assertEquals(linkedAt, result.providers[0].linkedAt)
        assertEquals(0, result.page)
        assertEquals(20, result.size)
        assertEquals(1, result.total)
    }

    @Test
    fun `listProviders - Returns 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.listProviders(userId, null, null)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `listProviders - Returns empty list when user has no providers`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { providerClaimsManager.findByUserId(userId) } returns emptyList()

        val result = controller.listProviders(userId, null, null)

        assertTrue(result.providers.isEmpty())
        assertEquals(0, result.total)
    }

    @Test
    fun `listProviders - Respects pagination parameters`() = runTest {
        val providers = listOf(
            mockProviderUserInfo(providerId = "discord"),
            mockProviderUserInfo(providerId = "google", subject = "109876543210"),
            mockProviderUserInfo(providerId = "github", subject = "42")
        )
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { providerClaimsManager.findByUserId(userId) } returns providers

        val result = controller.listProviders(userId, 1, 2)

        assertEquals(1, result.providers.size)
        assertEquals("github", result.providers[0].providerId)
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(3, result.total)
    }

    @Test
    fun `unlinkProvider - Deletes provider link and returns unlinked response`() = runTest {
        val providerInfo = mockProviderUserInfo()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, "discord") } returns providerInfo
        coEvery { providerClaimsManager.deleteProviderLink(userId, "discord") } returns 1

        val result = controller.unlinkProvider(userId, "discord")

        assertEquals(userId, result.userId)
        assertEquals("discord", result.providerId)
        assertTrue(result.unlinked)
        coVerify { providerClaimsManager.deleteProviderLink(userId, "discord") }
    }

    @Test
    fun `unlinkProvider - Returns 404 when user not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.unlinkProvider(userId, "discord")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun `unlinkProvider - Returns 404 when provider link not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, "discord") } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.unlinkProvider(userId, "discord")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}

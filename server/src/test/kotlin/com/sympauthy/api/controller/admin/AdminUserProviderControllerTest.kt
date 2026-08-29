package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.resource.admin.AdminUserProviderLinkInputResource
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.InteractiveFlowStepResult
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import io.micronaut.http.HttpStatus
import java.net.URI
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var clientRedirectUriManager: ClientRedirectUriManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var providerManager: ProviderManager

    @MockK
    lateinit var linkProviderManager: InteractiveFlowSessionLinkProviderManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    @InjectMockKs
    lateinit var controller: AdminUserProviderController

    private val userId: UUID = UUID.randomUUID()
    private val linkedAt: LocalDateTime = LocalDateTime.of(2026, 1, 15, 14, 30, 0)

    /**
     * A later sign-in with the same provider, so a resource fed from the fetch date fails the assertion.
     */
    private val lastFetchedAt: LocalDateTime = LocalDateTime.of(2026, 3, 2, 9, 0, 0)

    private fun mockProviderUserInfo(
        providerId: String = "discord",
        subject: String = "123456789012345678"
    ): ProviderUserInfo = ProviderUserInfo(
        providerId = providerId,
        userId = userId,
        linkDate = linkedAt,
        fetchDate = lastFetchedAt,
        changeDate = lastFetchedAt,
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
    fun `unlinkProvider - Deletes the provider link`() = runTest {
        val providerInfo = mockProviderUserInfo()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, "discord") } returns providerInfo
        coEvery { providerClaimsManager.deleteProviderLink(userId, "discord") } returns 1

        controller.unlinkProvider(userId, "discord")

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

    @Test
    fun `startLink - Validates user, client, provider and URI, starts an admin-initiated link session`() = runTest {
        val client = mockk<Client>()
        val returnUri = URI.create("https://client.example.com/linked")
        val flow = mockk<InteractiveFlow>()
        val session = mockk<OnGoingInteractiveFlowSession>()
        val steppedSession = mockk<OnGoingInteractiveFlowSession>()
        val nextUri = URI.create("https://auth.example.com/flow/confirm?state=abc")

        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { clientManager.findClientByIdOrNull("client-id") } returns client
        coEvery { providerManager.listEnabledProviders() } returns
            listOf(mockk<EnabledProvider> { every { id } returns "discord" })
        every {
            clientRedirectUriManager.parseRequestedRedirectUri(client, "https://client.example.com/linked", recoverable = true)
        } returns returnUri
        coEvery { interactiveAuthFlowSessionManager.getDefaultInteractiveFlow() } returns flow
        // Admin-initiated: initiatingClientId must be null (confirmation shows "an administrator").
        coEvery {
            linkProviderManager.startLinkProviderSession(userId, "discord", returnUri, flow, null, null)
        } returns session
        coEvery { engine.advance(session) } returns
            InteractiveFlowStepResult(steppedSession, InteractiveFlowStep.Confirm)
        coEvery { stepUriMapper.toRedirectUri(steppedSession, flow, InteractiveFlowStep.Confirm) } returns nextUri

        val result = controller.startLink(
            userId,
            "discord",
            AdminUserProviderLinkInputResource(
                clientId = "client-id",
                returnUri = "https://client.example.com/linked"
            )
        )

        assertEquals(nextUri.toString(), result.redirectUrl)
        coVerify { linkProviderManager.startLinkProviderSession(userId, "discord", returnUri, flow, null, null) }
    }

    @Test
    fun `startLink - Returns 404 when the user is not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.startLink(
                userId,
                "discord",
                AdminUserProviderLinkInputResource(clientId = "client-id", returnUri = "https://client.example.com/linked")
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        coVerify(exactly = 0) {
            linkProviderManager.startLinkProviderSession(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `startLink - Returns 404 when the named client is not found`() = runTest {
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { clientManager.findClientByIdOrNull("missing-client") } returns null

        val exception = assertThrows<LocalizedHttpException> {
            controller.startLink(
                userId,
                "discord",
                AdminUserProviderLinkInputResource(clientId = "missing-client", returnUri = "https://client.example.com/linked")
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        coVerify(exactly = 0) {
            linkProviderManager.startLinkProviderSession(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `startLink - Returns 404 and starts no session for an unknown provider`() = runTest {
        val client = mockk<Client>()
        coEvery { userManager.findByIdOrNull(userId) } returns mockk<User>()
        coEvery { clientManager.findClientByIdOrNull("client-id") } returns client
        // No enabled provider matches the path id -> orNotFound() -> 404, coherent with unknown user/client.
        coEvery { providerManager.listEnabledProviders() } returns emptyList()

        val exception = assertThrows<LocalizedHttpException> {
            controller.startLink(
                userId,
                "bad-provider",
                AdminUserProviderLinkInputResource(clientId = "client-id", returnUri = "https://client.example.com/linked")
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        coVerify(exactly = 0) {
            linkProviderManager.startLinkProviderSession(any(), any(), any(), any(), any(), any())
        }
    }
}

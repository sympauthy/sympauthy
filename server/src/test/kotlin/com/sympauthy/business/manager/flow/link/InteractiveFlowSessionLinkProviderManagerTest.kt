package com.sympauthy.business.manager.flow.link

import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.flow.confirm.InteractiveFlowSessionConfirmManager
import com.sympauthy.business.mapper.InteractiveFlowSessionLinkProviderMapper
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.repository.InteractiveFlowSessionLinkProviderRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionLinkProviderManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var confirmManager: InteractiveFlowSessionConfirmManager

    @MockK
    lateinit var linkProviderRepository: InteractiveFlowSessionLinkProviderRepository

    @MockK
    lateinit var linkProviderMapper: InteractiveFlowSessionLinkProviderMapper

    @MockK
    lateinit var userManager: UserManager

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionLinkProviderManager

    private fun stubStart(
        sessionId: UUID,
        userId: UUID,
        returnUri: URI,
        cancelUri: URI?,
        initiatingClientId: String?,
        flow: AuthorizationFlow,
    ): OnGoingInteractiveFlowSession {
        val newSession = mockk<OnGoingInteractiveFlowSession>()
        val withUser = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
        coEvery {
            sessionManager.newSession(
                purposes = listOf(
                    InteractiveFlowPurpose.CONFIRM,
                    InteractiveFlowPurpose.REAUTHENTICATION,
                    InteractiveFlowPurpose.LINK_PROVIDER,
                ),
                initiatingPurpose = InteractiveFlowPurpose.LINK_PROVIDER,
                flow = flow,
                successRedirectUri = returnUri,
                redirectType = InteractiveFlowRedirectType.PLAIN,
                cancelRedirectUri = cancelUri,
            )
        } returns newSession
        coJustRun { userManager.checkPromoted(userId) }
        coEvery { sessionManager.setAuthenticatedUserId(newSession, userId) } returns withUser
        coEvery {
            confirmManager.setConfirm(withUser, ConfirmActionType.LINK_PROVIDER, initiatingClientId)
        } returns mockk()
        coEvery { linkProviderRepository.findBySessionId(sessionId) } returns null
        coEvery { linkProviderRepository.save(any()) } returns mockk()
        every { linkProviderMapper.toInteractiveFlowSessionLinkProvider(any()) } returns mockk()
        return withUser
    }

    @Test
    fun `startLinkProviderSession - Builds a CONFIRM REAUTHENTICATION LINK_PROVIDER session and stores the intent`() =
        runTest {
            val userId = UUID.randomUUID()
            val sessionId = UUID.randomUUID()
            val returnUri = URI.create("https://client.example.com/linked")
            val cancelUri = URI.create("https://client.example.com/cancelled")
            val flow = mockk<AuthorizationFlow>()
            val withUser = stubStart(sessionId, userId, returnUri, cancelUri, "client-id", flow)

            val result = manager.startLinkProviderSession(
                userId = userId,
                providerId = "target-provider",
                returnUri = returnUri,
                flow = flow,
                initiatingClientId = "client-id",
                cancelUri = cancelUri,
            )

            assertSame(withUser, result)
            coVerify { confirmManager.setConfirm(withUser, ConfirmActionType.LINK_PROVIDER, "client-id") }
            coVerify {
                linkProviderRepository.save(withArg {
                    assertEquals(sessionId, it.sessionId)
                    assertEquals("target-provider", it.providerId)
                })
            }
        }

    @Test
    fun `startLinkProviderSession - Stores a null client id for an admin-initiated link`() = runTest {
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val returnUri = URI.create("https://client.example.com/linked")
        val flow = mockk<AuthorizationFlow>()
        // Admin-initiated: the confirm record must carry a null client id (rendered as "an administrator").
        stubStart(sessionId, userId, returnUri, null, null, flow)

        manager.startLinkProviderSession(
            userId = userId,
            providerId = "target-provider",
            returnUri = returnUri,
            flow = flow,
            initiatingClientId = null,
            cancelUri = null,
        )

        coVerify { confirmManager.setConfirm(any(), ConfirmActionType.LINK_PROVIDER, null) }
    }
}

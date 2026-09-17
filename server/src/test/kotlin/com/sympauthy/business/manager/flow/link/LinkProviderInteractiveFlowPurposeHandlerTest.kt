package com.sympauthy.business.manager.flow.link

import com.sympauthy.business.manager.flow.InteractiveFlowSessionProviderManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionLinkProvider
import com.sympauthy.business.model.flow.InteractiveFlowSessionProvider
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.business.model.provider.ProviderUserInfo
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
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class LinkProviderInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var linkProviderManager: InteractiveFlowSessionLinkProviderManager

    @MockK
    lateinit var providerClaimsManager: ProviderClaimsManager

    @MockK
    lateinit var providerManager: InteractiveFlowSessionProviderManager

    @InjectMockKs
    lateinit var handler: LinkProviderInteractiveFlowPurposeHandler

    private fun session(userId: UUID): OnGoingInteractiveFlowSession {
        val sessionId = UUID.randomUUID()
        return mockk {
            every { id } returns sessionId
            every { this@mockk.userId } returns userId
        }
    }

    /** The debug read never reaches for the user, so a session mock that stubs one is over-stubbed. */
    private fun sessionWithoutUser(): OnGoingInteractiveFlowSession {
        val sessionId = UUID.randomUUID()
        return mockk { every { id } returns sessionId }
    }

    @Test
    fun `nextStepOrNull - Drives to the target provider authorization when not yet linked`() = runTest {
        val userId = UUID.randomUUID()
        val session = session(userId)
        coEvery { linkProviderManager.fetchLinkProviderOrNull(session) } returns
            InteractiveFlowSessionLinkProvider(sessionId = session.id, providerId = "target-provider")
        coEvery { providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, "target-provider") } returns null

        val step = handler.nextStepOrNull(session)

        assertEquals(InteractiveFlowStep.AuthorizeProvider("target-provider"), step)
    }

    @Test
    fun `nextStepOrNull - Resolves once the target provider is linked to the session user`() = runTest {
        val userId = UUID.randomUUID()
        val session = session(userId)
        coEvery { linkProviderManager.fetchLinkProviderOrNull(session) } returns
            InteractiveFlowSessionLinkProvider(sessionId = session.id, providerId = "target-provider")
        coEvery { providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, "target-provider") } returns
            mockk<ProviderUserInfo>()

        assertNull(handler.nextStepOrNull(session))
    }

    @Test
    fun `debugInformation - Names the target provider and the one being authorized with`() = runTest {
        val session = sessionWithoutUser()
        coEvery { linkProviderManager.fetchLinkProviderOrNull(session) } returns
            InteractiveFlowSessionLinkProvider(sessionId = session.id, providerId = "target-provider")
        coEvery { providerManager.fetchProviderOrNull(session) } returns
            InteractiveFlowSessionProvider(sessionId = session.id, providerId = "target-provider")

        assertEquals(
            listOf(
                PurposeDebugInformation("Target provider", "target-provider"),
                PurposeDebugInformation("Provider authorization in flight", "target-provider")
            ),
            handler.debugInformation(session)
        )
    }

    @Test
    fun `debugInformation - Emits no provider in flight while none is`() = runTest {
        val session = sessionWithoutUser()
        coEvery { linkProviderManager.fetchLinkProviderOrNull(session) } returns
            InteractiveFlowSessionLinkProvider(sessionId = session.id, providerId = "target-provider")
        coEvery { providerManager.fetchProviderOrNull(session) } returns null

        val information = handler.debugInformation(session)

        assertNull(information.first { it.displayName == "Provider authorization in flight" }.value)
    }

    @Test
    fun `debugInformation - Answers for a session with no record that reached a terminal status`() = runTest {
        val failed = mockk<FailedInteractiveFlowSession>()
        coEvery { linkProviderManager.fetchLinkProviderOrNull(failed) } returns null
        coEvery { providerManager.fetchProviderOrNull(failed) } returns null

        assertEquals(2, handler.debugInformation(failed).size)
    }
}

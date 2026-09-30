package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.mapper.InteractiveFlowSessionOAuth2Mapper
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionOAuth2ManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var oauth2Repository: InteractiveFlowSessionOAuth2Repository

    @MockK
    lateinit var oauth2Mapper: InteractiveFlowSessionOAuth2Mapper

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionOAuth2Manager

    @Test
    fun `setAuthenticationDate - Write the moment the credential was proven`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { id } returns sessionId
            every { initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        }
        val written = slot<LocalDateTime>()
        coEvery { oauth2Repository.updateAuthenticationDate(sessionId, capture(written)) } returns 1

        val before = LocalDateTime.now()
        manager.setAuthenticationDate(session)

        assertTrue(written.isCaptured)
        assertFalse(written.captured.isBefore(before.minus(TOLERANCE)))
        assertFalse(written.captured.isAfter(LocalDateTime.now().plus(TOLERANCE)))
    }

    @Test
    fun `setAuthenticationDate - Refuse a write that moved no row`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { id } returns sessionId
            every { initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
        }
        coEvery { oauth2Repository.updateAuthenticationDate(sessionId, any()) } returns 0

        val exception = assertThrows<BusinessException> { manager.setAuthenticationDate(session) }

        assertEquals("auth.interactive_flow_session.oauth2.missing", exception.detailsId)
    }

    @Test
    fun `setAuthenticationDate - Refuse a session serving no authorization`() = runTest {
        // The id is left unstubbed: a session serving no authorization must be refused before anything
        // reaches for its record.
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { initiatingPurpose } returns InteractiveFlowPurpose.LINK_PROVIDER
        }

        val exception = assertThrows<BusinessException> { manager.setAuthenticationDate(session) }

        assertEquals("auth.interactive_flow_session.oauth2.wrong_type", exception.detailsId)
        coVerify(exactly = 0) { oauth2Repository.updateAuthenticationDate(any(), any()) }
    }

    private companion object {
        /**
         * The clock moves between the assertion's own reading and the manager's, and on a slow runner it
         * moves further than a millisecond.
         */
        val TOLERANCE: Duration = Duration.ofSeconds(5)
    }
}

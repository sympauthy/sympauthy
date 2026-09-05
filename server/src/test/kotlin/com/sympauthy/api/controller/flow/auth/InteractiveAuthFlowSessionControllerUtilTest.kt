package com.sympauthy.api.controller.flow.auth

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveAuthFlowSessionControllerUtilTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager

    @MockK
    lateinit var engine: InteractiveFlowEngine

    @MockK
    lateinit var stepUriMapper: InteractiveFlowStepUriMapper

    @InjectMockKs
    lateinit var util: InteractiveAuthFlowSessionControllerUtil

    private val concurrentModification = InteractiveFlowSessionManager.CONCURRENT_MODIFICATION_DETAILS_ID

    @Test
    fun `handleException - Returns the session unchanged when there is no exception`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()

        assertSame(session, util.handleException(session, null))
    }

    @Test
    fun `handleException - Rethrows a recoverable exception`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val error = recoverableBusinessExceptionOf("some.detail", "some.description")

        val thrown = assertThrows<BusinessException> { util.handleException(session, error) }
        assertSame(error, thrown)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `handleException - Routes a concurrent conflict by the current terminal session instead of failing it`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
        val completed = mockk<CompletedInteractiveFlowSession>()
        coEvery { sessionManager.fetchByIdOrNull(sessionId) } returns completed

        val result = util.handleException(session, businessExceptionOf(concurrentModification))

        // The concurrent winner completed the shared session, so this request is routed to that completed
        // session (→ success redirect), never failed.
        assertSame(completed, result)
        coVerify(exactly = 0) { sessionManager.markAsFailedIfNotRecoverable(any(), any()) }
    }

    @Test
    fun `handleException - Fails the session when the concurrent conflict is still ongoing`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }
        val stillOngoing = mockk<OnGoingInteractiveFlowSession>()
        val failed = mockk<FailedInteractiveFlowSession>()
        val error = businessExceptionOf(concurrentModification)
        coEvery { sessionManager.fetchByIdOrNull(sessionId) } returns stillOngoing
        coEvery { sessionManager.markAsFailedIfNotRecoverable(session, error) } returns failed

        val result = util.handleException(session, error)

        assertSame(failed, result)
    }

    @Test
    fun `handleException - Fails the session for a genuine non-recoverable error without re-fetching`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val failed = mockk<FailedInteractiveFlowSession>()
        val error = businessExceptionOf("flow.some.fatal")
        coEvery { sessionManager.markAsFailedIfNotRecoverable(session, error) } returns failed

        val result = util.handleException(session, error)

        assertSame(failed, result)
        coVerify(exactly = 0) { sessionManager.fetchByIdOrNull(any()) }
    }
}

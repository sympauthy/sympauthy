package com.sympauthy.api.controller.flow.auth

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.security.UserSecurityContextManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.security.observedRequestOf
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

    @MockK(relaxed = true)
    lateinit var userSecurityContextManager: UserSecurityContextManager

    @InjectMockKs
    lateinit var util: InteractiveAuthFlowSessionControllerUtil

    private val concurrentModification = InteractiveFlowSessionManager.CONCURRENT_MODIFICATION_DETAILS_ID

    private fun ongoing(sessionId: UUID) = mockk<OnGoingInteractiveFlowSession> {
        every { id } returns sessionId
        every { expired } returns false
    }

    /** Expired but still ongoing: the shape the cleaner collects, and the one the guard turns on. */
    private fun expiredOngoing() = mockk<OnGoingInteractiveFlowSession> {
        every { expired } returns true
    }

    private val observed = observedRequestOf()

    @Test
    fun `fetchSessionAndObserveRequest - Records where the request that resolved the session came from`() = runTest {
        val sessionId = UUID.randomUUID()
        val session = ongoing(sessionId)
        coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
            SuccessVerifyEncodedStateResult(session)

        assertSame(session, util.fetchSessionAndObserveRequest("encoded-state", observed))

        coVerify { userSecurityContextManager.observe(sessionId, observed) }
    }

    /**
     * Completing a flow folds its proven place into the person's record and consumes every row it held,
     * and the state outlives the flow — a replay must not re-open one.
     */
    @Test
    fun `fetchSessionAndObserveRequest - Records nothing against a session that has reached its end`() =
        runTest {
            val session = mockk<CompletedInteractiveFlowSession>()
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                SuccessVerifyEncodedStateResult(session)

            assertSame(session, util.fetchSessionAndObserveRequest("encoded-state", observed))

            coVerify(exactly = 0) { userSecurityContextManager.observe(any(), any()) }
        }

    /** The cleaner deletes an expired session and its places in one transaction; a row landing between
     * the two fails the delete's foreign key and rolls the whole collection back. */
    @Test
    fun `fetchSessionAndObserveRequest - Records nothing against a session the cleaner is entitled to`() =
        runTest {
            val session = expiredOngoing()
            coEvery { sessionManager.verifyEncodedInternalState("encoded-state") } returns
                SuccessVerifyEncodedStateResult(session)

            util.fetchSessionAndObserveRequest("encoded-state", observed)

            coVerify(exactly = 0) { userSecurityContextManager.observe(any(), any()) }
        }

    @Test
    fun `fetchSessionAndObserveRequest - Records nothing where the state named no session`() = runTest {
        coEvery { sessionManager.verifyEncodedInternalState("forged-state") } returns
            FailedVerifyEncodedStateResult("flow.state.invalid")

        assertThrows<LocalizedHttpException> { util.fetchSessionAndObserveRequest("forged-state", observed) }

        coVerify(exactly = 0) { userSecurityContextManager.observe(any(), any()) }
    }

    @Test
    fun `observeStartedSession - Records where the session a controller just created was started from`() =
        runTest {
            val sessionId = UUID.randomUUID()
            val session = mockk<OnGoingInteractiveFlowSession> { every { id } returns sessionId }

            util.observeStartedSession(session, observed)

            coVerify { userSecurityContextManager.observe(sessionId, observed) }
        }

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

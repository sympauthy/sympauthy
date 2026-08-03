package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.mapper.InteractiveFlowSessionMapper
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.jwt.DecodedJwt
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.repository.InteractiveFlowSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionManagerTest {

    @MockK
    lateinit var userManager: UserManager

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var sessionRepository: InteractiveFlowSessionRepository

    @MockK
    lateinit var sessionMapper: InteractiveFlowSessionMapper

    @MockK
    lateinit var uncheckedAuthConfig: AuthConfig

    @SpyK
    @InjectMockKs
    lateinit var interactiveFlowSessionManager: InteractiveFlowSessionManager

    /**
     * Detail id of the exception thrown when a version-guarded update loses its compare-and-swap.
     */
    private val concurrentModificationDetailsId = "auth.interactive_flow_session.concurrent_modification"

    /**
     * Build an ongoing session at [version] carrying enough state to drive every mutation under test.
     */
    private fun ongoingSession(
        id: UUID = UUID.randomUUID(),
        version: Long = STARTING_VERSION,
        purposes: List<InteractiveFlowPurpose> = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
        initiatingPurpose: InteractiveFlowPurpose = purposes.first(),
        completedPurposes: List<InteractiveFlowPurpose> = emptyList(),
        userId: UUID? = UUID.randomUUID(),
        redirectType: InteractiveFlowRedirectType? = InteractiveFlowRedirectType.AUTHORIZATION_CODE,
    ) = OnGoingInteractiveFlowSession(
        id = id,
        purposes = purposes,
        initiatingPurpose = initiatingPurpose,
        flowId = "flow-id",
        expirationDate = LocalDateTime.now().plusHours(1),
        sessionDate = LocalDateTime.now(),
        version = version,
        userId = userId,
        completedPurposes = completedPurposes,
        successRedirectUri = URI.create("https://client.example.com/callback"),
        redirectType = redirectType,
        cancelRedirectUri = URI.create("https://client.example.com/callback"),
    )

    @Test
    fun `verifyEncodedInternalState - Return failure when state is null`() = runTest {
        val result = interactiveFlowSessionManager.verifyEncodedInternalState(null)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.missing_state", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when state is blank`() = runTest {
        val result = interactiveFlowSessionManager.verifyEncodedInternalState("   ")

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.missing_state", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when JWT signature is invalid`() = runTest {
        val state = "invalid.jwt.token"
        coEvery {
            jwtManager.decodeAndVerifyOrNull(InteractiveFlowSessionManager.STATE_KEY_NAME, state)
        } returns null

        val result = interactiveFlowSessionManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.wrong_signature", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when JWT subject is not a valid UUID`() = runTest {
        val state = "valid.jwt.token"
        val jwt = DecodedJwt(id = null, subject = "not-a-uuid", keyId = null)
        coEvery {
            jwtManager.decodeAndVerifyOrNull(InteractiveFlowSessionManager.STATE_KEY_NAME, state)
        } returns jwt

        val result = interactiveFlowSessionManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.invalid_subject", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return failure when session is not found`() = runTest {
        val state = "valid.jwt.token"
        val sessionId = UUID.randomUUID()
        val jwt = DecodedJwt(id = null, subject = sessionId.toString(), keyId = null)
        coEvery {
            jwtManager.decodeAndVerifyOrNull(InteractiveFlowSessionManager.STATE_KEY_NAME, state)
        } returns jwt
        coEvery { sessionRepository.findById(sessionId) } returns null

        val result = interactiveFlowSessionManager.verifyEncodedInternalState(state)

        assertTrue(result is FailedVerifyEncodedStateResult)
        result as FailedVerifyEncodedStateResult
        assertEquals("auth.interactive_flow_session.validate.missing_session", result.detailsId)
    }

    @Test
    fun `verifyEncodedInternalState - Return success when state is valid`() = runTest {
        val state = "valid.jwt.token"
        val sessionId = UUID.randomUUID()
        val jwt = DecodedJwt(id = null, subject = sessionId.toString(), keyId = null)
        val entity = mockk<InteractiveFlowSessionEntity>()
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery {
            jwtManager.decodeAndVerifyOrNull(InteractiveFlowSessionManager.STATE_KEY_NAME, state)
        } returns jwt
        coEvery { sessionRepository.findById(sessionId) } returns entity
        every { sessionMapper.toInteractiveFlowSession(entity) } returns session

        val result = interactiveFlowSessionManager.verifyEncodedInternalState(state)

        assertTrue(result is SuccessVerifyEncodedStateResult)
        result as SuccessVerifyEncodedStateResult
        assertSame(session, result.session)
    }

    // region setAuthenticatedUserId

    @Test
    fun `setAuthenticatedUserId - Persists the user and bumps the version`() = runTest {
        val session = ongoingSession(userId = null)
        val userId = UUID.randomUUID()
        coEvery {
            sessionRepository.updateUserId(session.id, userId, true, STARTING_VERSION)
        } returns 1

        val result = interactiveFlowSessionManager.setAuthenticatedUserId(session, userId, signedUp = true)

        assertEquals(userId, result.userId)
        assertTrue(result.signedUp)
        assertEquals(STARTING_VERSION + 1, result.version)
    }

    @Test
    fun `setAuthenticatedUserId - Fails with a concurrent modification when the version is stale`() = runTest {
        val session = ongoingSession(userId = null)
        val userId = UUID.randomUUID()
        coEvery {
            sessionRepository.updateUserId(session.id, userId, false, STARTING_VERSION)
        } returns 0

        val exception = assertThrows<BusinessException> {
            interactiveFlowSessionManager.setAuthenticatedUserId(session, userId)
        }
        assertEquals(concurrentModificationDetailsId, exception.detailsId)
        assertFalse(exception.recoverable)
    }

    // endregion

    // region setMfaPassed

    @Test
    fun `setMfaPassed - Persists the date and bumps the version`() = runTest {
        val session = ongoingSession()
        coEvery {
            sessionRepository.updateMfaPassedDate(session.id, any(), STARTING_VERSION)
        } returns 1

        val result = interactiveFlowSessionManager.setMfaPassed(session)

        assertTrue(result.mfaPassed)
        assertEquals(STARTING_VERSION + 1, result.version)
    }

    @Test
    fun `setMfaPassed - Fails with a concurrent modification when the version is stale`() = runTest {
        val session = ongoingSession()
        coEvery {
            sessionRepository.updateMfaPassedDate(session.id, any(), STARTING_VERSION)
        } returns 0

        val exception = assertThrows<BusinessException> {
            interactiveFlowSessionManager.setMfaPassed(session)
        }
        assertEquals(concurrentModificationDetailsId, exception.detailsId)
    }

    // endregion

    // region insertPurposeAfter

    @Test
    fun `insertPurposeAfter - Inserts right after the given purpose, persists the list and bumps the version`() = runTest {
        val session = ongoingSession(
            purposes = listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            ),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
        )
        // Stub with the exact expected persisted names so reaching the assertion proves the right ordering was
        // saved: the inserted MFA_CHALLENGE lands right after REAUTHENTICATION, ahead of the trailing purpose.
        coEvery {
            sessionRepository.updatePurposes(
                session.id,
                match {
                    it.contentEquals(
                        arrayOf(
                            InteractiveFlowPurpose.CONFIRM.name,
                            InteractiveFlowPurpose.REAUTHENTICATION.name,
                            InteractiveFlowPurpose.MFA_CHALLENGE.name,
                            InteractiveFlowPurpose.MFA_ENROLLMENT.name,
                        )
                    )
                },
                STARTING_VERSION
            )
        } returns 1

        val result = interactiveFlowSessionManager.insertPurposeAfter(
            session,
            purpose = InteractiveFlowPurpose.MFA_CHALLENGE,
            afterPurpose = InteractiveFlowPurpose.REAUTHENTICATION,
        )

        assertEquals(
            listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.MFA_CHALLENGE,
                InteractiveFlowPurpose.MFA_ENROLLMENT,
            ),
            result.purposes
        )
        assertEquals(STARTING_VERSION + 1, result.version)
    }

    @Test
    fun `insertPurposeAfter - Fails with a concurrent modification when the version is stale`() = runTest {
        val session = ongoingSession(purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE))
        coEvery {
            sessionRepository.updatePurposes(session.id, any(), STARTING_VERSION)
        } returns 0

        val exception = assertThrows<BusinessException> {
            interactiveFlowSessionManager.insertPurposeAfter(
                session,
                purpose = InteractiveFlowPurpose.MFA_CHALLENGE,
                afterPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            )
        }
        assertEquals(concurrentModificationDetailsId, exception.detailsId)
    }

    // endregion

    // region markPurposeAsCompleted

    @Test
    fun `markPurposeAsCompleted - Records the purpose, bumps the version and stays ongoing`() = runTest {
        val session = ongoingSession(
            purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
        )
        // Stub with the exact expected persisted names so reaching the assertion proves the right list was saved.
        coEvery {
            sessionRepository.updateCompletedPurposes(
                session.id,
                match { it.contentEquals(arrayOf(InteractiveFlowPurpose.CONFIRM.name)) },
                STARTING_VERSION
            )
        } returns 1

        val result = interactiveFlowSessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.CONFIRM)

        assertEquals(listOf(InteractiveFlowPurpose.CONFIRM), result.completedPurposes)
        assertEquals(STARTING_VERSION + 1, result.version)
        // Bookkeeping only: the session must not be completed even though a purpose was recorded.
        coVerify(exactly = 0) { sessionRepository.updateCompleteDate(any(), any(), any()) }
    }

    @Test
    fun `markPurposeAsCompleted - Recording an already-completed purpose is a no-op on the list`() = runTest {
        val session = ongoingSession(
            purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
            completedPurposes = listOf(InteractiveFlowPurpose.CONFIRM),
        )
        coEvery {
            sessionRepository.updateCompletedPurposes(
                session.id,
                match { it.contentEquals(arrayOf(InteractiveFlowPurpose.CONFIRM.name)) },
                STARTING_VERSION
            )
        } returns 1

        val result = interactiveFlowSessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.CONFIRM)

        assertEquals(listOf(InteractiveFlowPurpose.CONFIRM), result.completedPurposes)
    }

    @Test
    fun `markPurposeAsCompleted - Fails with a concurrent modification when the version is stale`() = runTest {
        val session = ongoingSession(
            purposes = listOf(InteractiveFlowPurpose.CONFIRM, InteractiveFlowPurpose.MFA_ENROLLMENT),
        )
        coEvery {
            sessionRepository.updateCompletedPurposes(session.id, any(), STARTING_VERSION)
        } returns 0

        val exception = assertThrows<BusinessException> {
            interactiveFlowSessionManager.markPurposeAsCompleted(session, InteractiveFlowPurpose.CONFIRM)
        }
        assertEquals(concurrentModificationDetailsId, exception.detailsId)
    }

    // endregion

    // region markAsCompleted

    @Test
    fun `markAsCompleted - Persists the complete date and returns a completed session`() = runTest {
        val userId = UUID.randomUUID()
        val session = ongoingSession(
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE),
            completedPurposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE),
            userId = userId,
        )
        coEvery { sessionRepository.updateCompleteDate(session.id, any(), STARTING_VERSION) } returns 1

        val result = interactiveFlowSessionManager.markAsCompleted(session)

        assertEquals(userId, result.userId)
        assertEquals(
            listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE, InteractiveFlowPurpose.MFA_CHALLENGE),
            result.completedPurposes
        )
        assertEquals(InteractiveFlowRedirectType.AUTHORIZATION_CODE, result.redirectType)
    }

    @Test
    fun `markAsCompleted - Fails with a concurrent modification when the version is stale`() = runTest {
        val session = ongoingSession(userId = UUID.randomUUID())
        coEvery { sessionRepository.updateCompleteDate(session.id, any(), STARTING_VERSION) } returns 0

        val exception = assertThrows<BusinessException> {
            interactiveFlowSessionManager.markAsCompleted(session)
        }
        assertEquals(concurrentModificationDetailsId, exception.detailsId)
    }

    // endregion

    // region markAsCancelled

    @Test
    fun `markAsCancelled - Persists the cancel date and returns a cancelled session`() = runTest {
        val userId = UUID.randomUUID()
        val session = ongoingSession(userId = userId)
        coEvery { sessionRepository.updateCancelDate(session.id, any(), STARTING_VERSION) } returns 1

        val result = interactiveFlowSessionManager.markAsCancelled(session)

        assertEquals(session.id, result.id)
        assertEquals(userId, result.userId)
        assertEquals(InteractiveFlowRedirectType.AUTHORIZATION_CODE, result.redirectType)
    }

    @Test
    fun `markAsCancelled - Fails with a concurrent modification when the version is stale`() = runTest {
        val session = ongoingSession(userId = UUID.randomUUID())
        coEvery { sessionRepository.updateCancelDate(session.id, any(), STARTING_VERSION) } returns 0

        val exception = assertThrows<BusinessException> {
            interactiveFlowSessionManager.markAsCancelled(session)
        }
        assertEquals(concurrentModificationDetailsId, exception.detailsId)
    }

    @Test
    fun `markAsCancelled - Throws a recoverable error for a PLAIN session with no cancel target`() = runTest {
        val session = OnGoingInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.MFA_ENROLLMENT),
            initiatingPurpose = InteractiveFlowPurpose.MFA_ENROLLMENT,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            version = STARTING_VERSION,
            userId = UUID.randomUUID(),
            successRedirectUri = URI.create("https://client.example.com/enrolled"),
            redirectType = InteractiveFlowRedirectType.PLAIN,
            cancelRedirectUri = null,
        )

        val exception = assertThrows<BusinessException> {
            interactiveFlowSessionManager.markAsCancelled(session)
        }
        assertEquals("auth.interactive_flow_session.cancel.no_cancel_target", exception.detailsId)
        assertTrue(exception.recoverable)
        coVerify(exactly = 0) { sessionRepository.updateCancelDate(any(), any(), any()) }
    }

    // endregion

    // region markAsFailedIfNotRecoverable

    @Test
    fun `markAsFailedIfNotRecoverable - Leaves a recoverable error ongoing without persisting`() = runTest {
        val session = ongoingSession()
        val recoverable = recoverableBusinessExceptionOf("some.detail", "some.description")

        val result = interactiveFlowSessionManager.markAsFailedIfNotRecoverable(session, recoverable)

        assertSame(session, result)
        coVerify(exactly = 0) { sessionRepository.bumpVersion(any(), any()) }
        coVerify(exactly = 0) { sessionRepository.updateError(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `markAsFailedIfNotRecoverable - Persists and returns a failed session for a non-recoverable error`() = runTest {
        val session = ongoingSession()
        val error = businessExceptionOf("some.detail")
        coEvery { sessionRepository.bumpVersion(session.id, STARTING_VERSION) } returns 1
        coEvery { sessionRepository.updateError(session.id, any(), "some.detail", null, any()) } just Runs

        val result = interactiveFlowSessionManager.markAsFailedIfNotRecoverable(session, error)

        assertTrue(result is FailedInteractiveFlowSession)
        result as FailedInteractiveFlowSession
        assertEquals("some.detail", result.errorDetailsId)
    }

    @Test
    fun `markAsFailedIfNotRecoverable - Swallows a lost swap without writing and still returns a failed session`() = runTest {
        val session = ongoingSession()
        val error = businessExceptionOf("some.detail")
        // A concurrent winner already advanced the row: the compare-and-swap affects 0 rows, so the
        // error write must be skipped (no throw, no clobber) while this request is still routed to the
        // error page.
        coEvery { sessionRepository.bumpVersion(session.id, STARTING_VERSION) } returns 0

        val result = interactiveFlowSessionManager.markAsFailedIfNotRecoverable(session, error)

        assertTrue(result is FailedInteractiveFlowSession)
        result as FailedInteractiveFlowSession
        assertEquals("some.detail", result.errorDetailsId)
        coVerify(exactly = 0) { sessionRepository.updateError(any(), any(), any(), any(), any()) }
    }

    // endregion

    companion object {
        /**
         * Non-zero starting version so a `+ 1` bump on success is a meaningful assertion.
         */
        private const val STARTING_VERSION = 3L
    }
}

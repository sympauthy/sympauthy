package com.sympauthy.data.repository

import com.sympauthy.data.model.InteractiveFlowSessionEntity
import com.sympauthy.data.model.UserEntity
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

/**
 * H2-backed test of the versioned optimistic-concurrency updates on [InteractiveFlowSessionRepository].
 *
 * Beyond the compare-and-swap semantics (affected-row count `1` vs `0`, version increment), this
 * exercises the two bindings that have no other precedent in the codebase: the `text array` columns
 * (`purposes`, `completed_purposes`) and the `json` column (`error_values`) bound as parameters inside
 * a raw `@Query`.
 */
@MicronautTest(
    environments = ["default", "test", "h2"],
    startApplication = false,
    transactional = false
)
class InteractiveFlowSessionRepositoryTest {

    @Inject
    lateinit var sessionRepository: InteractiveFlowSessionRepository

    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var userId: UUID
    private val createdSessionIds = mutableListOf<UUID>()

    @BeforeEach
    fun setUp() = runTest {
        userId = UserEntity(status = "enabled", creationDate = LocalDateTime.now())
            .also { userRepository.save(it) }
            .id!!
    }

    @AfterEach
    fun tearDown() = runTest {
        if (createdSessionIds.isNotEmpty()) {
            sessionRepository.deleteByIds(createdSessionIds)
            createdSessionIds.clear()
        }
        userRepository.deleteById(userId)
    }

    private suspend fun newSession(
        purposes: Array<String> = arrayOf("OAUTH2_AUTHORIZE"),
    ): InteractiveFlowSessionEntity {
        val now = LocalDateTime.now()
        return InteractiveFlowSessionEntity(
            purposes = purposes,
            initiatingPurpose = purposes.first(),
            sessionDate = now,
            flowId = "flow",
            expirationDate = now.plusMinutes(10),
        ).also {
            sessionRepository.save(it)
            createdSessionIds.add(it.id!!)
        }
    }

    @Test
    fun `updatePurposes - binds the array, applies at the expected version and increments it`() = runTest {
        val session = newSession()

        val updated = sessionRepository.updatePurposes(
            id = session.id!!,
            purposes = arrayOf("OAUTH2_AUTHORIZE", "MFA_CHALLENGE"),
            expectedVersion = 0
        )

        assertEquals(1, updated)
        val reloaded = sessionRepository.findById(session.id!!)!!
        assertTrue(arrayOf("OAUTH2_AUTHORIZE", "MFA_CHALLENGE").contentEquals(reloaded.purposes))
        assertEquals(1L, reloaded.version)
    }

    @Test
    fun `updatePurposes - a stale expected version affects no rows and leaves the row untouched`() = runTest {
        val session = newSession()
        // Advance the row to version 1 so the original snapshot (version 0) is now stale.
        sessionRepository.updatePurposes(session.id!!, arrayOf("MFA_CHALLENGE"), expectedVersion = 0)

        val updated = sessionRepository.updatePurposes(
            id = session.id!!,
            purposes = arrayOf("OAUTH2_AUTHORIZE"),
            expectedVersion = 0
        )

        assertEquals(0, updated)
        val reloaded = sessionRepository.findById(session.id!!)!!
        assertTrue(arrayOf("MFA_CHALLENGE").contentEquals(reloaded.purposes))
        assertEquals(1L, reloaded.version)
    }

    @Test
    fun `updateCompletedPurposes - binds the array and increments the version`() = runTest {
        val session = newSession()

        val updated = sessionRepository.updateCompletedPurposes(
            id = session.id!!,
            completedPurposes = arrayOf("OAUTH2_AUTHORIZE"),
            expectedVersion = 0
        )

        assertEquals(1, updated)
        val reloaded = sessionRepository.findById(session.id!!)!!
        assertTrue(arrayOf("OAUTH2_AUTHORIZE").contentEquals(reloaded.completedPurposes))
        assertEquals(1L, reloaded.version)
    }

    @Test
    fun `updateError - the derived write binds and round-trips the json values`() = runTest {
        val session = newSession()

        sessionRepository.updateError(
            id = session.id!!,
            errorDate = LocalDateTime.now(),
            errorDetailsId = "some.error",
            errorDescriptionId = "some.description",
            errorValues = mapOf("key" to "value"),
        )

        val reloaded = sessionRepository.findById(session.id!!)!!
        assertEquals("some.error", reloaded.errorDetailsId)
        assertEquals(mapOf("key" to "value"), reloaded.errorValues)
    }

    @Test
    fun `failIfOngoing - bumps the version while ongoing and refuses once terminal`() = runTest {
        val session = newSession()
        val id = session.id!!

        // Ongoing: the guard bumps the version regardless of its current value.
        assertEquals(1, sessionRepository.failIfOngoing(id))
        assertEquals(1L, sessionRepository.findById(id)!!.version)

        // Drive it to a terminal (completed) state; the guard must then refuse.
        sessionRepository.updateCompleteDate(id, LocalDateTime.now(), expectedVersion = 1)
        assertEquals(0, sessionRepository.failIfOngoing(id))
    }

    @Test
    fun `updateUserId - applies against the user foreign key and increments the version`() = runTest {
        val session = newSession()

        val updated = sessionRepository.updateUserId(
            id = session.id!!,
            userId = userId,
            signedUp = true,
            expectedVersion = 0
        )

        assertEquals(1, updated)
        val reloaded = sessionRepository.findById(session.id!!)!!
        assertEquals(userId, reloaded.userId)
        assertTrue(reloaded.signedUp)
        assertEquals(1L, reloaded.version)
    }

    @Test
    fun `scalar updates - each applies at the expected version and increments it`() = runTest {
        val session = newSession()
        val id = session.id!!

        assertEquals(1, sessionRepository.updateMfaPassedDate(id, LocalDateTime.now(), expectedVersion = 0))
        assertEquals(1, sessionRepository.updateCompleteDate(id, LocalDateTime.now(), expectedVersion = 1))
        assertEquals(1, sessionRepository.updateCancelDate(id, LocalDateTime.now(), expectedVersion = 2))

        val reloaded = sessionRepository.findById(id)!!
        assertEquals(3L, reloaded.version)
    }

    @Test
    fun `a stale expected version never mutates the row`() = runTest {
        val session = newSession()
        val id = session.id!!

        val updated = sessionRepository.updateMfaPassedDate(id, LocalDateTime.now(), expectedVersion = 99)

        assertEquals(0, updated)
        val reloaded = sessionRepository.findById(id)!!
        assertNull(reloaded.mfaPassedDate)
        assertEquals(0L, reloaded.version)
    }
}

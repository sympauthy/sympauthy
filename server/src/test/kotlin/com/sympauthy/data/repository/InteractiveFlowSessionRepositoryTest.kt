package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime

/**
 * The versioned optimistic-concurrency updates on [InteractiveFlowSessionRepository].
 *
 * Beyond the compare-and-swap semantics (affected-row count `1` vs `0`, version increment), this
 * exercises the array columns `purposes` and `completed_purposes` bound as parameters inside a raw
 * `@Query`, and `error_values` written through the derived [InteractiveFlowSessionRepository.updateError]
 * — a `json` column has to be, since a map bound into a raw query is stored as its `toString()`.
 */
class InteractiveFlowSessionRepositoryTest {

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the purpose arrays and the error values`(database: Database) = withFixture(database) {
        val sessions = repository<InteractiveFlowSessionRepository>()
        val session = newSession(purposes = arrayOf("OAUTH2_AUTHORIZE", "MFA_CHALLENGE"))

        val stored = sessions.findById(session.id!!)

        assertNotNull(stored)
        assertArrayEquals(arrayOf("OAUTH2_AUTHORIZE", "MFA_CHALLENGE"), stored!!.purposes)
        assertArrayEquals(emptyArray<String>(), stored.completedPurposes)
        assertEquals("OAUTH2_AUTHORIZE", stored.initiatingPurpose)
        assertEquals(BASE_DATE, stored.sessionDate)
        assertEquals(0L, stored.version)
        assertNull(stored.errorValues)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByCode - Joins the authorization code back to its session`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val session = newSession()
            newCode(session.id!!, "interactive-flow-session-repository-test-code")

            val found = sessions.findByCode("interactive-flow-session-repository-test-code")

            assertEquals(session.id, found?.id)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByCode - Returns null when no code holds that value`(database: Database) = withFixture(database) {
        val sessions = repository<InteractiveFlowSessionRepository>()
        newSession()

        assertNull(sessions.findByCode("interactive-flow-session-repository-test-absent"))
    }

    /**
     * `CURRENT_TIMESTAMP` is the database's clock, not the JVM's, so the two sessions are dated a year
     * either side of now rather than around [BASE_DATE].
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findExpired - Returns the sessions whose expiration has passed`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val now = LocalDateTime.now()
            val expired = newSession(expirationDate = now.minusYears(1))
            val ongoing = newSession(expirationDate = now.plusYears(1))

            val found = sessions.findExpired().map { it.id }

            assertTrue(found.contains(expired.id))
            assertTrue(!found.contains(ongoing.id))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updatePurposes - Binds the array, applies at the expected version and increments it`(
        database: Database
    ) = withFixture(database) {
        val sessions = repository<InteractiveFlowSessionRepository>()
        val session = newSession()

        val updated = sessions.updatePurposes(
            id = session.id!!,
            purposes = arrayOf("OAUTH2_AUTHORIZE", "MFA_CHALLENGE"),
            expectedVersion = 0
        )

        assertEquals(1, updated)
        val reloaded = sessions.findById(session.id!!)!!
        assertArrayEquals(arrayOf("OAUTH2_AUTHORIZE", "MFA_CHALLENGE"), reloaded.purposes)
        assertEquals(1L, reloaded.version)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updatePurposes - A stale expected version affects no rows and leaves the row untouched`(
        database: Database
    ) = withFixture(database) {
        val sessions = repository<InteractiveFlowSessionRepository>()
        val session = newSession()
        // Advance the row to version 1 so the original snapshot (version 0) is now stale.
        sessions.updatePurposes(session.id!!, arrayOf("MFA_CHALLENGE"), expectedVersion = 0)

        val updated = sessions.updatePurposes(
            id = session.id!!,
            purposes = arrayOf("OAUTH2_AUTHORIZE"),
            expectedVersion = 0
        )

        assertEquals(0, updated)
        val reloaded = sessions.findById(session.id!!)!!
        assertArrayEquals(arrayOf("MFA_CHALLENGE"), reloaded.purposes)
        assertEquals(1L, reloaded.version)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateCompletedPurposes - Binds the array and increments the version`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val session = newSession()

            val updated = sessions.updateCompletedPurposes(
                id = session.id!!,
                completedPurposes = arrayOf("OAUTH2_AUTHORIZE"),
                expectedVersion = 0
            )

            assertEquals(1, updated)
            val reloaded = sessions.findById(session.id!!)!!
            assertArrayEquals(arrayOf("OAUTH2_AUTHORIZE"), reloaded.completedPurposes)
            assertEquals(1L, reloaded.version)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateError - The derived write binds and round-trips the json values`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val session = newSession()

            sessions.updateError(
                id = session.id!!,
                errorDate = BASE_DATE.plusMinutes(1),
                errorDetailsId = "some.error",
                errorDescriptionId = "some.description",
                errorValues = mapOf("key" to "value")
            )

            val reloaded = sessions.findById(session.id!!)!!
            assertEquals(BASE_DATE.plusMinutes(1), reloaded.errorDate)
            assertEquals("some.error", reloaded.errorDetailsId)
            assertEquals("some.description", reloaded.errorDescriptionId)
            assertEquals(mapOf("key" to "value"), reloaded.errorValues)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `failIfOngoing - Bumps the version while ongoing and refuses once terminal`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val id = newSession().id!!

            // Ongoing: the guard bumps the version regardless of its current value.
            assertEquals(1, sessions.failIfOngoing(id))
            assertEquals(1L, sessions.findById(id)!!.version)

            // Drive it to a terminal (completed) state; the guard must then refuse.
            sessions.updateCompleteDate(id, BASE_DATE.plusMinutes(2), expectedVersion = 1)
            assertEquals(0, sessions.failIfOngoing(id))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateUserId - Applies against the user foreign key and increments the version`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val userId = newUser()
            val session = newSession()

            val updated = sessions.updateUserId(
                id = session.id!!,
                userId = userId,
                signedUp = true,
                expectedVersion = 0
            )

            assertEquals(1, updated)
            val reloaded = sessions.findById(session.id!!)!!
            assertEquals(userId, reloaded.userId)
            assertTrue(reloaded.signedUp)
            assertEquals(1L, reloaded.version)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateMfaPassedDate - Applies at the expected version and increments it`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val id = newSession().id!!

            val updated = sessions.updateMfaPassedDate(id, BASE_DATE.plusMinutes(1), expectedVersion = 0)

            assertEquals(1, updated)
            val reloaded = sessions.findById(id)!!
            assertEquals(BASE_DATE.plusMinutes(1), reloaded.mfaPassedDate)
            assertEquals(1L, reloaded.version)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateCompleteDate - Completes the session at the expected version`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val id = newSession().id!!

            val updated = sessions.updateCompleteDate(id, BASE_DATE.plusMinutes(1), expectedVersion = 0)

            assertEquals(1, updated)
            val reloaded = sessions.findById(id)!!
            assertEquals(BASE_DATE.plusMinutes(1), reloaded.completeDate)
            assertEquals(1L, reloaded.version)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateCancelDate - Cancels the session at the expected version`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val id = newSession().id!!

            val updated = sessions.updateCancelDate(id, BASE_DATE.plusMinutes(1), expectedVersion = 0)

            assertEquals(1, updated)
            val reloaded = sessions.findById(id)!!
            assertEquals(BASE_DATE.plusMinutes(1), reloaded.cancelDate)
            assertEquals(1L, reloaded.version)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateMfaPassedDate - A stale expected version never mutates the row`(database: Database) =
        withFixture(database) {
            val sessions = repository<InteractiveFlowSessionRepository>()
            val id = newSession().id!!

            val updated = sessions.updateMfaPassedDate(id, BASE_DATE.plusMinutes(1), expectedVersion = 99)

            assertEquals(0, updated)
            val reloaded = sessions.findById(id)!!
            assertNull(reloaded.mfaPassedDate)
            assertEquals(0L, reloaded.version)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteByIds - Removes every session named and counts them`(database: Database) = withFixture(database) {
        val sessions = repository<InteractiveFlowSessionRepository>()
        val deleted = newSession().id!!
        val alsoDeleted = newSession().id!!
        val kept = newSession().id!!

        val count = sessions.deleteByIds(listOf(deleted, alsoDeleted))

        assertEquals(2, count)
        assertNull(sessions.findById(deleted))
        assertNull(sessions.findById(alsoDeleted))
        assertNotNull(sessions.findById(kept))
    }

}

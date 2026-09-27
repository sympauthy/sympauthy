package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.withFixture
import com.sympauthy.util.isRowAlreadyThere
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The code handed back from an authorize request, keyed by the session it belongs to rather than by an
 * identifier of its own, so the insert has to carry the key it was given.
 */
class AuthorizationCodeRepositoryTest {

    private val code = "authorization-code-repository-test-code"
    private val otherCode = "authorization-code-repository-test-other-code"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and round-trips the row`(database: Database) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val session = newSession()

        newCode(session.id!!, code)

        val stored = codes.findById(session.id!!)
        assertNotNull(stored)
        assertEquals(session.id, stored!!.sessionId)
        assertEquals(code, stored.code)
        assertEquals(BASE_DATE, stored.creationDate)
        assertEquals(BASE_DATE.plusMinutes(10), stored.expirationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteByCode - Removes the code it names`(database: Database) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val session = newSession()
        val other = newSession()
        newCode(session.id!!, code)
        newCode(other.id!!, otherCode)

        codes.deleteByCode(code)

        assertNull(codes.findById(session.id!!))
        assertNotNull(codes.findById(other.id!!))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the codes of every session and counts them`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val deleted = newSession()
            val alsoDeleted = newSession()
            val kept = newSession()
            newCode(deleted.id!!, code)
            newCode(alsoDeleted.id!!, otherCode)
            newCode(kept.id!!, "authorization-code-repository-test-kept-code")

            val count = codes.deleteBySessionIdIn(listOf(deleted.id!!, alsoDeleted.id!!))

            assertEquals(2, count)
            assertNull(codes.findById(deleted.id!!))
            assertNull(codes.findById(alsoDeleted.id!!))
            assertNotNull(codes.findById(kept.id!!))
        }

    /**
     * A departure from leaving a constraint to the database: `AuthorizationCodeManager` reads this refusal
     * as the session having already taken its code and answers the replay with it, so a dialect refusing it
     * as something else leaves that branch unreachable and answers a replay with a `500`.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Refuses a second code for one session, on either dialect`(database: Database) =
        withFixture(database) {
            val session = newSession()
            newCode(session.id!!, code)

            val refusal = assertThrows<Exception> { newCode(session.id!!, otherCode) }

            assertTrue(refusal.isRowAlreadyThere(), "The refusal does not read as the key being taken.")
        }

    /**
     * The other way the write is refused, and the reason that branch reads the refusal rather than the
     * driver's whole class of integrity failures: a session that is gone is this server's own failure.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Refuses a code for a session that is not there, as something else`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val entity = AuthorizationCodeEntity(
                sessionId = UUID.randomUUID(),
                code = code,
                creationDate = BASE_DATE,
                expirationDate = BASE_DATE.plusMinutes(10)
            )

            val refusal = assertThrows<Exception> { codes.save(entity) }

            assertFalse(refusal.isRowAlreadyThere(), "A session that is gone reads as the key being taken.")
        }
}

package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    @ParameterizedTest(name = "save - Carries the assigned key and round-trips the row on {0}")
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and round-trips the row`(database: Database) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val session = newSession()

        saveCode(session.id!!, code)

        val stored = codes.findById(session.id!!)
        assertNotNull(stored)
        assertEquals(session.id, stored!!.sessionId)
        assertEquals(code, stored.code)
        assertEquals(BASE_DATE, stored.creationDate)
        assertEquals(BASE_DATE.plusMinutes(10), stored.expirationDate)
    }

    @ParameterizedTest(name = "deleteByCode - Removes the code it names on {0}")
    @EnumSource(Database::class)
    fun `deleteByCode - Removes the code it names`(database: Database) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val session = newSession()
        val other = newSession()
        saveCode(session.id!!, code)
        saveCode(other.id!!, otherCode)

        codes.deleteByCode(code)

        assertNull(codes.findById(session.id!!))
        assertNotNull(codes.findById(other.id!!))
    }

    @ParameterizedTest(name = "deleteBySessionIdIn - Removes the codes of every session and counts them on {0}")
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the codes of every session and counts them`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val deleted = newSession()
            val alsoDeleted = newSession()
            val kept = newSession()
            saveCode(deleted.id!!, code)
            saveCode(alsoDeleted.id!!, otherCode)
            saveCode(kept.id!!, "authorization-code-repository-test-kept-code")

            val count = codes.deleteBySessionIdIn(listOf(deleted.id!!, alsoDeleted.id!!))

            assertEquals(2, count)
            assertNull(codes.findById(deleted.id!!))
            assertNull(codes.findById(alsoDeleted.id!!))
            assertNotNull(codes.findById(kept.id!!))
        }

    private suspend fun RepositoryFixture.saveCode(sessionId: UUID, code: String) {
        val codes = repository<AuthorizationCodeRepository>()
        codes.save(
            AuthorizationCodeEntity(
                sessionId = sessionId,
                code = code,
                creationDate = BASE_DATE,
                expirationDate = BASE_DATE.plusMinutes(10)
            )
        )
        deleteOnEnd { codes.deleteBySessionIdIn(listOf(sessionId)) }
    }
}

package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.ValidationCodeEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The code sent to prove a medium, whose `reasons` is an array column and whose two deletes take a
 * collection of identifiers.
 *
 * Every code belongs to a session and no two of a session's codes share a value: `session_id` is
 * `NOT NULL` and `(session_id, code)` unique.
 */
class ValidationCodeRepositoryTest {

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the reason array`(database: Database) = withFixture(database) {
        val codes = repository<ValidationCodeRepository>()
        val userId = newUser()
        val session = newSession()
        val id = saveCode(userId, session.id!!, reasons = arrayOf("EMAIL_CLAIM", "RESET_PASSWORD"))

        val stored = codes.findById(id)

        assertNotNull(stored)
        assertArrayEquals(arrayOf("EMAIL_CLAIM", "RESET_PASSWORD"), stored!!.reasons)
        assertEquals("EMAIL", stored.media)
        assertEquals(BASE_DATE, stored.creationDate)
        assertEquals(session.id, stored.sessionId)
        assertNull(stored.validationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Returns the codes of the session`(database: Database) = withFixture(database) {
        val codes = repository<ValidationCodeRepository>()
        val userId = newUser()
        val session = newSession()
        val other = newSession()
        val id = saveCode(userId, session.id!!)
        saveCode(userId, other.id!!)

        val found = codes.findBySessionId(session.id!!)

        assertEquals(listOf(id), found.map { it.id!! })
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionIdAndMedia - Narrows the session codes to one medium`(database: Database) =
        withFixture(database) {
            val codes = repository<ValidationCodeRepository>()
            val userId = newUser()
            val session = newSession()
            val byEmail = saveCode(userId, session.id!!, media = "EMAIL")
            saveCode(userId, session.id!!, media = "PHONE_NUMBER", code = "654321")

            val found = codes.findBySessionIdAndMedia(session.id!!, "EMAIL")

            assertEquals(listOf(byEmail), found.map { it.id!! })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteByIds - Removes every code named`(database: Database) = withFixture(database) {
        val codes = repository<ValidationCodeRepository>()
        val userId = newUser()
        val session = newSession()
        val deleted = saveCode(userId, session.id!!, code = "100001")
        val alsoDeleted = saveCode(userId, session.id!!, code = "100002")
        val kept = saveCode(userId, session.id!!, code = "100003")

        codes.deleteByIds(listOf(deleted, alsoDeleted))

        assertNull(codes.findById(deleted))
        assertNull(codes.findById(alsoDeleted))
        assertNotNull(codes.findById(kept))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the codes of every session and counts them`(database: Database) =
        withFixture(database) {
            val codes = repository<ValidationCodeRepository>()
            val userId = newUser()
            val session = newSession()
            val otherSession = newSession()
            val keptSession = newSession()
            val deleted = saveCode(userId, session.id!!)
            val alsoDeleted = saveCode(userId, otherSession.id!!)
            val kept = saveCode(userId, keptSession.id!!)

            val count = codes.deleteBySessionIdIn(listOf(session.id!!, otherSession.id!!))

            assertEquals(2, count)
            assertNull(codes.findById(deleted))
            assertNull(codes.findById(alsoDeleted))
            assertNotNull(codes.findById(kept))
        }

    private suspend fun RepositoryFixture.saveCode(
        userId: UUID,
        sessionId: UUID,
        media: String = "EMAIL",
        reasons: Array<String> = arrayOf("EMAIL_CLAIM"),
        code: String = "123456"
    ): UUID {
        val codes = repository<ValidationCodeRepository>()
        return codes.save(
            ValidationCodeEntity(
                code = code,
                userId = userId,
                media = media,
                reasons = reasons,
                sessionId = sessionId,
                creationDate = BASE_DATE,
                resendDate = null,
                expirationDate = BASE_DATE.plusMinutes(10)
            )
        ).id!!.also { id -> deleteOnEnd { codes.deleteByIds(listOf(id)) } }
    }
}

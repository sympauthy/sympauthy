package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.InteractiveFlowSessionPasswordResetEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The progress of a password reset, keyed by the session it hangs off, whose new password is stored as empty
 * binary columns until it is chosen.
 */
class InteractiveFlowSessionPasswordResetRepositoryTest {

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Stores a reset no password was chosen for yet as empty binary columns`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionPasswordResetRepository>()
            val session = newSession()

            saveRecord(session.id!!, userId = null)

            val stored = records.findBySessionId(session.id!!)
            assertNotNull(stored)
            assertEquals("person@example.com", stored!!.login)
            assertNull(stored.userId)
            assertEquals(BASE_DATE, stored.requestedDate)
            assertEquals(0, stored.salt.size)
            assertEquals(0, stored.hashedPassword.size)
            assertNull(stored.passwordChosenDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `update - Round-trips the code proven and the password chosen`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionPasswordResetRepository>()
            val session = newSession()
            val userId = newUser()
            saveRecord(session.id!!, userId = userId)

            records.update(
                InteractiveFlowSessionPasswordResetEntity(
                    sessionId = session.id!!,
                    login = "person@example.com",
                    userId = userId,
                    requestedDate = BASE_DATE,
                    codeProvenDate = BASE_DATE.plusMinutes(1),
                    salt = byteArrayOf(1, 2, 3),
                    hashedPassword = byteArrayOf(4, 5, 6),
                    passwordChosenDate = BASE_DATE.plusMinutes(2)
                )
            )

            val stored = records.findBySessionId(session.id!!)!!
            assertEquals(userId, stored.userId)
            assertEquals(BASE_DATE.plusMinutes(1), stored.codeProvenDate)
            assertArrayEquals(byteArrayOf(1, 2, 3), stored.salt)
            assertArrayEquals(byteArrayOf(4, 5, 6), stored.hashedPassword)
            assertEquals(BASE_DATE.plusMinutes(2), stored.passwordChosenDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the records and counts them`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionPasswordResetRepository>()
        val deleted = newSession()
        val kept = newSession()
        saveRecord(deleted.id!!, userId = null)
        saveRecord(kept.id!!, userId = null)

        assertEquals(1, records.deleteBySessionIdIn(listOf(deleted.id!!)))

        assertNull(records.findBySessionId(deleted.id!!))
        assertNotNull(records.findBySessionId(kept.id!!))
    }

    private suspend fun RepositoryFixture.saveRecord(sessionId: UUID, userId: UUID?) {
        val records = repository<InteractiveFlowSessionPasswordResetRepository>()
        records.save(
            InteractiveFlowSessionPasswordResetEntity(
                sessionId = sessionId,
                login = "person@example.com",
                userId = userId,
                requestedDate = BASE_DATE
            )
        )
        deleteOnEnd { records.deleteBySessionIdIn(listOf(sessionId)) }
    }
}

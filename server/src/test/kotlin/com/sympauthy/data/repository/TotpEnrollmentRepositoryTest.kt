package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.TotpEnrollmentEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/**
 * The TOTP secret and the date it was confirmed, which the repository reads through a pair of derived
 * names that differ only by the null test they carry.
 */
class TotpEnrollmentRepositoryTest {

    private val secret = byteArrayOf(11, 22, 33, 44, 55)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the secret and leaves it unconfirmed`(database: Database) = withFixture(database) {
        val enrollments = repository<TotpEnrollmentRepository>()
        val userId = newUser()
        val id = saveEnrollment(userId)

        val stored = enrollments.findById(id)

        assertNotNull(stored)
        assertArrayEquals(secret, stored!!.secret)
        assertEquals(userId, stored.userId)
        assertEquals(BASE_DATE, stored.creationDate)
        assertNull(stored.confirmedDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserId - Returns every enrollment of the user`(database: Database) = withFixture(database) {
        val enrollments = repository<TotpEnrollmentRepository>()
        val userId = newUser()
        val otherUserId = newUser()
        val pending = saveEnrollment(userId)
        val confirmed = saveEnrollment(userId, confirmedDate = BASE_DATE.plusMinutes(1))
        saveEnrollment(otherUserId)

        val found = enrollments.findByUserId(userId)

        assertEquals(setOf(pending, confirmed), found.map { it.id!! }.toSet())
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdAndConfirmedDateIsNotNull - Keeps the confirmed enrollment`(database: Database) =
        withFixture(database) {
            val enrollments = repository<TotpEnrollmentRepository>()
            val userId = newUser()
            saveEnrollment(userId)
            val confirmed = saveEnrollment(userId, confirmedDate = BASE_DATE.plusMinutes(1))

            val found = enrollments.findByUserIdAndConfirmedDateIsNotNull(userId)

            assertEquals(listOf(confirmed), found.map { it.id!! })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdAndConfirmedDateIsNull - Keeps the pending enrollment`(database: Database) =
        withFixture(database) {
            val enrollments = repository<TotpEnrollmentRepository>()
            val userId = newUser()
            val pending = saveEnrollment(userId)
            saveEnrollment(userId, confirmedDate = BASE_DATE.plusMinutes(1))

            val found = enrollments.findByUserIdAndConfirmedDateIsNull(userId)

            assertEquals(listOf(pending), found.map { it.id!! })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateConfirmedDate - Confirms the enrollment`(database: Database) = withFixture(database) {
        val enrollments = repository<TotpEnrollmentRepository>()
        val userId = newUser()
        val id = saveEnrollment(userId)
        val confirmedAt = BASE_DATE.plusMinutes(1)

        enrollments.updateConfirmedDate(id, confirmedAt)

        assertEquals(confirmedAt, enrollments.findById(id)?.confirmedDate)
        assertEquals(listOf(id), enrollments.findByUserIdAndConfirmedDateIsNotNull(userId).map { it.id!! })
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdAndSessionIdIsNull - Hides an enrollment a session is still signing up`(database: Database) =
        withFixture(database) {
            val enrollments = repository<TotpEnrollmentRepository>()
            val session = newSession()
            val userId = newUser(sessionId = session.id)
            val id = saveEnrollment(userId, confirmedDate = BASE_DATE, sessionId = session.id)

            assertNull(enrollments.findByIdAndSessionIdIsNull(id))

            enrollments.clearSessionId(userId, session.id!!)

            assertEquals(id, enrollments.findByIdAndSessionIdIsNull(id)?.id)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `clearSessionId - Promotes the enrollments of that session, confirmed or not`(database: Database) =
        withFixture(database) {
            val enrollments = repository<TotpEnrollmentRepository>()
            val session = newSession()
            val otherSession = newSession()
            val userId = newUser(sessionId = session.id)
            val otherUserId = newUser(sessionId = otherSession.id)
            val confirmed = saveEnrollment(userId, confirmedDate = BASE_DATE, sessionId = session.id)
            val pending = saveEnrollment(userId, sessionId = session.id)
            val untouched = saveEnrollment(otherUserId, sessionId = otherSession.id)

            assertEquals(2, enrollments.clearSessionId(userId, session.id!!))

            assertNull(enrollments.findById(confirmed)?.sessionId)
            assertEquals(BASE_DATE, enrollments.findById(confirmed)?.confirmedDate)
            assertNull(enrollments.findById(pending)?.sessionId)
            assertEquals(otherSession.id, enrollments.findById(untouched)?.sessionId)
        }

    private suspend fun RepositoryFixture.saveEnrollment(
        userId: UUID,
        confirmedDate: LocalDateTime? = null,
        sessionId: UUID? = null
    ): UUID {
        val enrollments = repository<TotpEnrollmentRepository>()
        return enrollments.save(
            TotpEnrollmentEntity(
                userId = userId,
                secret = secret,
                creationDate = BASE_DATE,
                confirmedDate = confirmedDate,
                sessionId = sessionId
            )
        ).id!!.also { id -> deleteOnEnd { enrollments.deleteById(id) } }
    }
}

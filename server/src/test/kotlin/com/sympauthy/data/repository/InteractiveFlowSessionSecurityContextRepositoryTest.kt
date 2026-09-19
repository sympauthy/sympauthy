package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/**
 * The places a session was driven from, held against it until the flow folds or expires.
 *
 * The upsert is spelled once per dialect, so every case here is a case about SQL no dialect shares.
 */
class InteractiveFlowSessionSecurityContextRepositoryTest {

    private val fingerprint = "c".repeat(64)
    private val otherFingerprint = "d".repeat(64)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `observe - Opens the place and round-trips the row`(database: Database) = withFixture(database) {
        val session = newSession()

        assertEquals(1, observe(session.id!!, userAgent = "Mozilla/5.0", city = "Lyon"))

        val stored = places(session.id!!).single()
        assertNotNull(stored.id)
        assertEquals(session.id, stored.sessionId)
        assertEquals(fingerprint, stored.fingerprint)
        assertEquals("203.0.113.7", stored.ip)
        assertEquals("Mozilla/5.0", stored.userAgent)
        assertEquals("Lyon", stored.city)
        assertEquals(BASE_DATE, stored.firstSeenDate)
        assertEquals(BASE_DATE, stored.lastSeenDate)
        assertEquals(1, stored.observationCount)
        assertNull(stored.provenDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `observe - Admits a request that carried no user agent and no location`(database: Database) =
        withFixture(database) {
            val session = newSession()

            observe(session.id!!)

            val stored = places(session.id!!).single()
            assertNull(stored.userAgent)
            assertNull(stored.city)
            assertNull(stored.countryCode)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `observe - Bumps the place a session was already seen at rather than opening a second`(
        database: Database
    ) = withFixture(database) {
        val session = newSession()
        observe(session.id!!, city = "Lyon")

        assertEquals(1, observe(session.id!!, observedDate = BASE_DATE.plusMinutes(5), city = "Paris"))

        val stored = places(session.id!!).single()
        assertEquals(2, stored.observationCount)
        assertEquals(BASE_DATE, stored.firstSeenDate)
        assertEquals(BASE_DATE.plusMinutes(5), stored.lastSeenDate)
        // A place seen again says where that address is now, not where an edge put it the first time.
        assertEquals("Paris", stored.city)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `observe - Opens a second row for a place the session has not been seen at`(database: Database) =
        withFixture(database) {
            val session = newSession()
            observe(session.id!!)

            observe(session.id!!, fingerprint = otherFingerprint, ip = "198.51.100.9")

            val stored = places(session.id!!).sortedBy { it.fingerprint }
            assertEquals(listOf(fingerprint, otherFingerprint).sorted(), stored.map { it.fingerprint })
            assertTrue(stored.all { it.observationCount == 1 })
        }

    /** The place is recorded by the request; the proof only stamps the row that request already wrote. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `markProven - Stamps the place it names and leaves what the requests recorded`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()
            val session = newSession()
            observe(session.id!!)

            assertEquals(1, records.markProven(session.id!!, fingerprint, BASE_DATE.plusMinutes(1)))
            observe(session.id!!, observedDate = BASE_DATE.plusMinutes(2))

            val stored = places(session.id!!).single()
            assertEquals(BASE_DATE.plusMinutes(1), stored.provenDate)
            assertEquals(BASE_DATE.plusMinutes(2), stored.lastSeenDate)
            assertEquals(2, stored.observationCount)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `markProven - Stamps no place but the one it names`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionSecurityContextRepository>()
        val session = newSession()
        val other = newSession()
        observe(session.id!!)
        observe(session.id!!, fingerprint = otherFingerprint)
        observe(other.id!!)

        records.markProven(session.id!!, fingerprint, BASE_DATE)

        assertEquals(
            listOf(fingerprint),
            places(session.id!!).filter { it.provenDate != null }.map { it.fingerprint }
        )
        assertNull(places(other.id!!).single().provenDate)
    }

    /** A place rolled out before the proof landed: the sighting is lost rather than a row opened. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `markProven - Answers zero where the session no longer holds that place`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()
            val session = newSession()
            observe(session.id!!)

            assertEquals(0, records.markProven(session.id!!, otherFingerprint, BASE_DATE))

            assertEquals(1, places(session.id!!).size)
        }

    /** Zero is what tells the caller to make room, rather than a failure. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `observe - Moves no row for a new place once the session holds as many as it may`(
        database: Database
    ) = withFixture(database) {
        val session = newSession()
        observe(session.id!!, maxPlaces = 1)

        assertEquals(0, observe(session.id!!, fingerprint = otherFingerprint, maxPlaces = 1))

        assertEquals(listOf(fingerprint), places(session.id!!).map { it.fingerprint })
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteLeastRecentPlace - Drops the place nobody has been seen at for longest`(
        database: Database
    ) = withFixture(database) {
        val session = newSession()
        observe(session.id!!, observedDate = BASE_DATE.plusMinutes(5))
        observe(session.id!!, fingerprint = otherFingerprint, observedDate = BASE_DATE)

        assertEquals(1, repository<InteractiveFlowSessionSecurityContextRepository>()
            .deleteLeastRecentPlace(session.id!!))

        assertEquals(listOf(fingerprint), places(session.id!!).map { it.fingerprint })
    }

    /** The proven place is the one the fold reads, so every other place makes room before it. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteLeastRecentPlace - Drops a place only seen before one a credential was proven at`(
        database: Database
    ) = withFixture(database) {
        val session = newSession()
        observe(session.id!!, observedDate = BASE_DATE)
        repository<InteractiveFlowSessionSecurityContextRepository>()
            .markProven(session.id!!, fingerprint, BASE_DATE)
        observe(session.id!!, fingerprint = otherFingerprint, observedDate = BASE_DATE.plusMinutes(5))

        repository<InteractiveFlowSessionSecurityContextRepository>().deleteLeastRecentPlace(session.id!!)

        assertEquals(listOf(fingerprint), places(session.id!!).map { it.fingerprint })
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteLeastRecentPlace - Leaves the places of another session alone`(database: Database) =
        withFixture(database) {
            val session = newSession()
            val other = newSession()
            observe(session.id!!)
            observe(other.id!!)

            repository<InteractiveFlowSessionSecurityContextRepository>().deleteLeastRecentPlace(session.id!!)

            assertEquals(emptyList<String>(), places(session.id!!).map { it.fingerprint })
            assertEquals(listOf(fingerprint), places(other.id!!).map { it.fingerprint })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteLeastRecentPlace - Answers zero where a session holds no place`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()

            assertEquals(0, records.deleteLeastRecentPlace(newSession().id!!))
        }

    /** At the cap a session goes on counting the places it already holds. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `observe - Bumps a place it already holds once the session holds as many as it may`(
        database: Database
    ) = withFixture(database) {
        val session = newSession()
        observe(session.id!!, maxPlaces = 1)

        assertEquals(1, observe(session.id!!, observedDate = BASE_DATE.plusMinutes(5), maxPlaces = 1))

        assertEquals(2, places(session.id!!).single().observationCount)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `observe - Keeps the places of one session apart from another's`(database: Database) =
        withFixture(database) {
            val session = newSession()
            val other = newSession()
            observe(session.id!!)

            observe(other.id!!, maxPlaces = 1)

            assertEquals(1, places(session.id!!).single().observationCount)
            assertEquals(1, places(other.id!!).single().observationCount)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Answers every place of that session and none of another's`(database: Database) =
        withFixture(database) {
            val session = newSession()
            val other = newSession()
            observe(session.id!!)
            observe(session.id!!, fingerprint = otherFingerprint)

            assertEquals(2, places(session.id!!).size)
            assertEquals(emptyList<UUID>(), places(other.id!!).map { it.sessionId })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes every place of the sessions it names`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()
            val deleted = newSession()
            val kept = newSession()
            observe(deleted.id!!)
            observe(deleted.id!!, fingerprint = otherFingerprint)
            observe(kept.id!!)

            assertEquals(2, records.deleteBySessionIdIn(listOf(deleted.id!!)))

            assertEquals(emptyList<UUID>(), places(deleted.id!!).map { it.id })
            assertEquals(1, places(kept.id!!).size)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Answers zero where a session left no place`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionSecurityContextRepository>()

            assertEquals(0, records.deleteBySessionIdIn(listOf(newSession().id!!)))
        }

    private suspend fun RepositoryFixture.places(sessionId: UUID) =
        repository<InteractiveFlowSessionSecurityContextRepository>().findBySessionId(sessionId)

    @Suppress("LongParameterList")
    private suspend fun RepositoryFixture.observe(
        sessionId: UUID,
        fingerprint: String = this@InteractiveFlowSessionSecurityContextRepositoryTest.fingerprint,
        ip: String = "203.0.113.7",
        userAgent: String? = null,
        city: String? = null,
        observedDate: LocalDateTime = BASE_DATE,
        maxPlaces: Int = 10
    ): Int {
        val records = repository<InteractiveFlowSessionSecurityContextRepository>()
        val moved = records.observe(
            sessionId = sessionId,
            fingerprint = fingerprint,
            ip = ip,
            userAgent = userAgent,
            countryCode = null,
            regionCode = null,
            region = null,
            city = city,
            timeZone = null,
            observedDate = observedDate,
            maxPlaces = maxPlaces
        )
        deleteOnEnd { records.deleteBySessionIdIn(listOf(sessionId)) }
        return moved
    }
}

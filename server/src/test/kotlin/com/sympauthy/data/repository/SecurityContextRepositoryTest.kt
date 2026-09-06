package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.SecurityContextEntity
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
 * Every query [SecurityContextRepository] declares, and the round-trip of a row whose optional half is
 * absent — the context nobody is attached to and no proxy placed, which is the shape the unconfigured
 * deployment writes.
 *
 * The fingerprints name this class so that no other class's rows fall inside the queries keyed on one.
 * `deleteExpired` has no key to name: it sweeps the whole table against the database's own clock, so it
 * is held to the row this test expired being gone and the one it left live still being there.
 */
class SecurityContextRepositoryTest {

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trip a context with every column set`(database: Database) = withFixture(database) {
        val userId = newUser()
        val id = saveContext(
            fingerprint = "round-trip-full",
            userId = userId,
            ip = "198.51.100.10",
            userAgent = "Mozilla/5.0",
            country = "FR",
            region = "OCC",
            city = "Toulouse",
            observationCount = 42
        )

        val stored = repository<SecurityContextRepository>().findById(id)

        assertNotNull(stored)
        assertEquals(userId, stored!!.userId)
        assertEquals("round-trip-full", stored.fingerprint)
        assertEquals("198.51.100.10", stored.ip)
        assertEquals("Mozilla/5.0", stored.userAgent)
        assertEquals("FR", stored.country)
        assertEquals("OCC", stored.region)
        assertEquals("Toulouse", stored.city)
        assertEquals(BASE_DATE, stored.firstSeenDate)
        assertEquals(BASE_DATE, stored.lastSeenDate)
        assertEquals(42, stored.observationCount)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trip a context nobody is attached to and no proxy placed`(database: Database) =
        withFixture(database) {
            val id = saveContext(fingerprint = "round-trip-empty")

            val stored = repository<SecurityContextRepository>().findById(id)

            assertNotNull(stored)
            assertNull(stored!!.userId)
            assertNull(stored.ip)
            assertNull(stored.userAgent)
            assertNull(stored.country)
            assertNull(stored.region)
            assertNull(stored.city)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findPastByUserId - Answer the places a user was seen, latest first`(database: Database) =
        withFixture(database) {
            val userId = newUser()
            val current = saveContext(fingerprint = "history-current", userId = userId)
            val older = saveContext(fingerprint = "history-older", userId = userId, lastSeenDate = BASE_DATE)
            val newer = saveContext(
                fingerprint = "history-newer",
                userId = userId,
                lastSeenDate = BASE_DATE.plusDays(1)
            )

            val history = repository<SecurityContextRepository>()
                .findPastByUserId(userId, excluding = current, limit = 10)

            assertEquals(listOf(newer, older), history.map { it.id })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findPastByUserId - Answer no more places than the deployment asked for`(database: Database) =
        withFixture(database) {
            val userId = newUser()
            val current = saveContext(fingerprint = "bounded-current", userId = userId)
            saveContext(fingerprint = "bounded-older", userId = userId, lastSeenDate = BASE_DATE)
            val newer = saveContext(
                fingerprint = "bounded-newer",
                userId = userId,
                lastSeenDate = BASE_DATE.plusDays(1)
            )

            val history = repository<SecurityContextRepository>()
                .findPastByUserId(userId, excluding = current, limit = 1)

            assertEquals(listOf(newer), history.map { it.id })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findFirstByUserIdAndFingerprint - Answer the place this user was seen in`(database: Database) =
        withFixture(database) {
            val userId = newUser()
            val id = saveContext(fingerprint = "known-place", userId = userId)

            val found = repository<SecurityContextRepository>()
                .findFirstByUserIdAndFingerprintOrderByFirstSeenDate(userId, "known-place")

            assertEquals(id, found?.id)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findFirstByUserIdAndFingerprint - Answer nothing for a place this user was not seen in`(
        database: Database
    ) = withFixture(database) {
        val userId = newUser()
        saveContext(fingerprint = "known-place-of-another", userId = newUser())

        val found = repository<SecurityContextRepository>()
            .findFirstByUserIdAndFingerprintOrderByFirstSeenDate(userId, "known-place-of-another")

        assertNull(found)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdIn - Answer the contexts a session carries the ids of`(database: Database) =
        withFixture(database) {
            val first = saveContext(fingerprint = "carried-first")
            val second = saveContext(fingerprint = "carried-second")
            saveContext(fingerprint = "carried-by-nobody")

            val carried = repository<SecurityContextRepository>().findByIdIn(listOf(first, second))

            assertEquals(setOf(first, second), carried.map { it.id }.toSet())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteExpired - Delete the contexts whose retention has run out and keep the rest`(
        database: Database
    ) = withFixture(database) {
        val contexts = repository<SecurityContextRepository>()
        val expired = saveContext(fingerprint = "expired", expirationDate = BASE_DATE)
        val live = saveContext(fingerprint = "live", expirationDate = LocalDateTime.now().plusHours(1))

        val deleted = contexts.deleteExpired()

        assertTrue(deleted >= 1, "The expired context was not deleted.")
        assertNull(contexts.findById(expired))
        assertNotNull(contexts.findById(live))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteByIdIn - Delete the contexts named and answer how many`(database: Database) =
        withFixture(database) {
            val contexts = repository<SecurityContextRepository>()
            val first = saveContext(fingerprint = "deleted-first")
            val second = saveContext(fingerprint = "deleted-second")
            val kept = saveContext(fingerprint = "deleted-none")

            val deleted = contexts.deleteByIdIn(listOf(first, second))

            assertEquals(2, deleted)
            assertNull(contexts.findById(first))
            assertNull(contexts.findById(second))
            assertNotNull(contexts.findById(kept))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateLastSeenDate - Move the sighting on and leave the rest of the row`(database: Database) =
        withFixture(database) {
            val contexts = repository<SecurityContextRepository>()
            val id = saveContext(fingerprint = "seen-again", ip = "198.51.100.10", country = "FR")
            val seenAt = BASE_DATE.plusDays(3)

            val updated = contexts.updateLastSeenDate(
                id = id,
                lastSeenDate = seenAt,
                observationCount = 2,
                expirationDate = seenAt.plusDays(180)
            )

            assertEquals(1, updated)
            val stored = contexts.findById(id)
            assertNotNull(stored)
            assertEquals(seenAt, stored!!.lastSeenDate)
            assertEquals(2, stored.observationCount)
            assertEquals(seenAt.plusDays(180), stored.expirationDate)
            assertEquals(BASE_DATE, stored.firstSeenDate)
            assertEquals("198.51.100.10", stored.ip)
            assertEquals("FR", stored.country)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateUserId - Attach the context to a user and re-stamp its expiration`(database: Database) =
        withFixture(database) {
            val contexts = repository<SecurityContextRepository>()
            val userId = newUser()
            val id = saveContext(fingerprint = "promoted", expirationDate = BASE_DATE.plusDays(1))

            val updated = contexts.updateUserId(
                id = id,
                userId = userId,
                expirationDate = BASE_DATE.plusDays(180)
            )

            assertEquals(1, updated)
            val stored = contexts.findById(id)
            assertNotNull(stored)
            assertEquals(userId, stored!!.userId)
            assertEquals(BASE_DATE.plusDays(180), stored.expirationDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateFirstSeenDate - Absorb the sightings of the context it replaces`(database: Database) =
        withFixture(database) {
            val contexts = repository<SecurityContextRepository>()
            val userId = newUser()
            val id = saveContext(
                fingerprint = "survivor",
                userId = userId,
                lastSeenDate = BASE_DATE.plusDays(3),
                observationCount = 7
            )

            val updated = contexts.updateFirstSeenDate(
                id = id,
                firstSeenDate = BASE_DATE.minusDays(1),
                lastSeenDate = BASE_DATE.plusDays(5),
                observationCount = 9,
                expirationDate = BASE_DATE.plusDays(185)
            )

            assertEquals(1, updated)
            val stored = contexts.findById(id)
            assertNotNull(stored)
            assertEquals(BASE_DATE.minusDays(1), stored!!.firstSeenDate)
            assertEquals(BASE_DATE.plusDays(5), stored.lastSeenDate)
            assertEquals(9, stored.observationCount)
            assertEquals(BASE_DATE.plusDays(185), stored.expirationDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateLastDecision - Record what the last access review answered`(database: Database) =
        withFixture(database) {
            val contexts = repository<SecurityContextRepository>()
            val id = saveContext(fingerprint = "reviewed")

            val updated = contexts.updateLastDecision(
                id = id,
                lastDecision = "ALLOW",
                lastDecisionDate = BASE_DATE.plusDays(1)
            )

            assertEquals(1, updated)
            val stored = contexts.findById(id)
            assertNotNull(stored)
            assertEquals("ALLOW", stored!!.lastDecision)
            assertEquals(BASE_DATE.plusDays(1), stored.lastDecisionDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteByUserIdIn - Free the account of the places it was seen in`(database: Database) =
        withFixture(database) {
            val contexts = repository<SecurityContextRepository>()
            val users = repository<UserRepository>()
            val userId = newUser()
            saveContext(fingerprint = "collected-with-the-account", userId = userId)

            val deleted = contexts.deleteByUserIdIn(listOf(userId))

            assertEquals(1, deleted)
            // The delete below is the point: the foreign key would refuse it while a context named the
            // account, and the sweep that runs it is one transaction for every expired session there is.
            assertEquals(1, users.deleteByIdIn(listOf(userId)))
        }

    @Suppress("LongParameterList")
    private suspend fun RepositoryFixture.saveContext(
        fingerprint: String,
        userId: UUID? = null,
        ip: String? = null,
        userAgent: String? = null,
        country: String? = null,
        region: String? = null,
        city: String? = null,
        lastSeenDate: LocalDateTime = BASE_DATE,
        observationCount: Int = 1,
        expirationDate: LocalDateTime = BASE_DATE.plusDays(180)
    ): UUID {
        val contexts = repository<SecurityContextRepository>()
        return contexts.save(
            SecurityContextEntity(
                userId = userId,
                fingerprint = fingerprint,
                ip = ip,
                userAgent = userAgent,
                country = country,
                region = region,
                city = city,
                firstSeenDate = BASE_DATE,
                lastSeenDate = lastSeenDate,
                observationCount = observationCount,
                expirationDate = expirationDate
            )
        ).id!!.also { id -> deleteOnEnd { contexts.deleteById(id) } }
    }
}

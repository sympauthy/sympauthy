package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.bean
import com.sympauthy.data.model.UserSecurityContextEntity
import com.sympauthy.data.withFixture
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/**
 * The places a person signs in from: the key two sightings collapse on, and the claim the sweep takes.
 *
 * The claim is raw SQL, so it is proved here against a real database of every dialect — and the one
 * thing about it that is not in the Kotlin, that a second run skips what a first is holding rather than
 * waiting for it, is driven over a connection of its own.
 */
class UserSecurityContextRepositoryTest {

    private val fingerprint = "a".repeat(64)
    private val otherFingerprint = "b".repeat(64)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Generates a key and round-trips the row`(database: Database) = withFixture(database) {
        val contexts = repository<UserSecurityContextRepository>()
        val userId = newUser()

        val saved = saveContext(userId, fingerprint, userAgent = "Mozilla/5.0", city = "Lyon")

        val stored = contexts.findById(saved.id!!)
        assertNotNull(stored)
        assertEquals(userId, stored!!.userId)
        assertEquals(fingerprint, stored.fingerprint)
        assertEquals("203.0.113.7", stored.ip)
        assertEquals("Mozilla/5.0", stored.userAgent)
        assertEquals("Lyon", stored.city)
        assertEquals(1, stored.observationCount)
        assertEquals(BASE_DATE, stored.firstSeenDate)
        assertEquals(BASE_DATE, stored.lastSeenDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Admits a row with no location at all`(database: Database) = withFixture(database) {
        val userId = newUser()

        val saved = saveContext(userId, fingerprint)

        val stored = repository<UserSecurityContextRepository>().findById(saved.id!!)
        assertNull(stored!!.city)
        assertNull(stored.countryCode)
        assertNull(stored.timeZone)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdAndFingerprint - Answers the place that person was seen at`(database: Database) =
        withFixture(database) {
            val contexts = repository<UserSecurityContextRepository>()
            val userId = newUser()
            saveContext(userId, fingerprint)

            val found = contexts.findByUserIdAndFingerprint(userId, fingerprint)

            assertNotNull(found)
            assertEquals(fingerprint, found!!.fingerprint)
        }

    /** The same place seen by two people is two rows, and neither reads the other's. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdAndFingerprint - Does not answer another person's place`(database: Database) =
        withFixture(database) {
            val contexts = repository<UserSecurityContextRepository>()
            val userId = newUser()
            val otherUserId = newUser()
            saveContext(otherUserId, fingerprint)

            assertNull(contexts.findByUserIdAndFingerprint(userId, fingerprint))
        }

    /**
     * A departure from leaving a constraint to the database: `UserSecurityContextManager` catches this
     * exact violation and folds again as an update, so a dialect not enforcing the index leaves that
     * branch unreachable and duplicates a person's places instead. The H2 spelling of `consents` drops
     * its partial unique index, which is what makes proving this one on every dialect worth a case.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Refuses a second row for one place and one person, on either dialect`(database: Database) =
        withFixture(database) {
            val userId = newUser()
            saveContext(userId, fingerprint)

            assertThrows<R2dbcDataIntegrityViolationException> { saveContext(userId, fingerprint) }
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateLastSeenDate - Moves the sighting, the count and the location`(database: Database) =
        withFixture(database) {
            val contexts = repository<UserSecurityContextRepository>()
            val userId = newUser()
            val saved = saveContext(userId, fingerprint, city = "Lyon")
            val seenAgain = BASE_DATE.plusDays(3)

            contexts.updateLastSeenDate(
                id = saved.id!!,
                lastSeenDate = seenAgain,
                observationCount = 2,
                countryCode = "FR",
                regionCode = null,
                region = null,
                city = "Paris",
                timeZone = "Europe/Paris"
            )

            val stored = contexts.findById(saved.id!!)!!
            assertEquals(seenAgain, stored.lastSeenDate)
            assertEquals(2, stored.observationCount)
            assertEquals("Paris", stored.city)
            assertEquals("FR", stored.countryCode)
            // The first sighting is what the row was opened with and is never moved.
            assertEquals(BASE_DATE, stored.firstSeenDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `claimExpired - Takes only what was last seen before the cutoff`(database: Database) =
        withFixture(database) {
            val contexts = repository<UserSecurityContextRepository>()
            val userId = newUser()
            val stale = saveContext(userId, fingerprint, lastSeenDate = BASE_DATE.minusDays(200))
            val fresh = saveContext(userId, otherFingerprint, lastSeenDate = BASE_DATE)

            val claimed = contexts.claimExpired(BASE_DATE.minusDays(100), limit = 1_000).map { it.id }

            assertTrue(stale.id in claimed, "The stale place was not claimed.")
            assertFalse(fresh.id in claimed, "A place seen since the cutoff was claimed.")
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `claimExpired - Takes no more than it asked for`(database: Database) = withFixture(database) {
        val contexts = repository<UserSecurityContextRepository>()
        val userId = newUser()
        saveContext(userId, fingerprint, lastSeenDate = BASE_DATE.minusDays(200))
        saveContext(userId, otherFingerprint, lastSeenDate = BASE_DATE.minusDays(200))

        assertEquals(1, contexts.claimExpired(BASE_DATE, limit = 1).size)
        assertEquals(2, contexts.claimExpired(BASE_DATE, limit = 2).size)
    }

    /**
     * `FOR UPDATE SKIP LOCKED` is what lets two instances sweep at once without either waiting, and a
     * test whose transactions ran one after the other would assert a property that held before it was
     * written. So the holder is driven over a connection of its own and its transaction stays open.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `claimExpired - Skips what another run is holding rather than waiting for it`(database: Database) =
        withFixture(database) {
            val contexts = repository<UserSecurityContextRepository>()
            val userId = newUser()
            val held = saveContext(userId, fingerprint, lastSeenDate = BASE_DATE.minusDays(200))
            val free = saveContext(userId, otherFingerprint, lastSeenDate = BASE_DATE.minusDays(200))
            val holder = database.bean<ConnectionFactory>().create().awaitFirst()

            try {
                holder.hold(held.id!!)

                val claimed = contexts.claimExpired(BASE_DATE, limit = 1_000).map { it.id }

                assertFalse(held.id in claimed, "A place another run was holding was claimed anyway.")
                assertTrue(free.id in claimed, "A place nobody held was skipped, so nothing was claimed at all.")
            } finally {
                holder.rollbackTransaction().awaitFirstOrNull()
                holder.close().awaitFirstOrNull()
            }
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteByIdInAndLastSeenDateLessThan - Removes what it claimed`(database: Database) =
        withFixture(database) {
            val contexts = repository<UserSecurityContextRepository>()
            val userId = newUser()
            val stale = saveContext(userId, fingerprint, lastSeenDate = BASE_DATE.minusDays(200))

            val deleted = contexts.deleteByIdInAndLastSeenDateLessThan(listOf(stale.id!!), BASE_DATE)

            assertEquals(1, deleted)
            assertNull(contexts.findById(stale.id!!))
        }

    /**
     * The fold writes this table too, so a place somebody signed in from again between the claim and the
     * delete has to survive it. The predicate is re-asserted for exactly this.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteByIdInAndLastSeenDateLessThan - Leaves a row seen again since it was claimed`(
        database: Database
    ) = withFixture(database) {
        val contexts = repository<UserSecurityContextRepository>()
        val userId = newUser()
        val seenAgain = saveContext(userId, fingerprint, lastSeenDate = BASE_DATE.plusDays(1))

        val deleted = contexts.deleteByIdInAndLastSeenDateLessThan(listOf(seenAgain.id!!), BASE_DATE)

        assertEquals(0, deleted)
        assertNotNull(contexts.findById(seenAgain.id!!))
    }

    /** Opens a transaction on this connection and holds [id] in it, leaving both open. */
    private suspend fun Connection.hold(id: UUID) {
        beginTransaction().awaitFirstOrNull()
        createStatement("SELECT id FROM user_security_contexts WHERE id = '$id' FOR UPDATE")
            .execute()
            .awaitFirst()
            .map { row, _ -> row.get(0) }
            .awaitFirstOrNull()
    }

    private suspend fun RepositoryFixture.saveContext(
        userId: UUID,
        fingerprint: String,
        userAgent: String? = null,
        city: String? = null,
        lastSeenDate: LocalDateTime = BASE_DATE
    ): UserSecurityContextEntity {
        val contexts = repository<UserSecurityContextRepository>()
        return contexts.save(
            UserSecurityContextEntity(
                userId = userId,
                fingerprint = fingerprint,
                ip = "203.0.113.7",
                userAgent = userAgent,
                city = city,
                firstSeenDate = BASE_DATE,
                lastSeenDate = lastSeenDate
            )
        ).also { saved -> deleteOnEnd { contexts.deleteById(saved.id!!) } }
    }
}

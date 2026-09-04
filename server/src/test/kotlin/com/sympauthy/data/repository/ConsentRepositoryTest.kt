package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.ConsentEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/**
 * The paging and counting queries [ConsentRepository] declares as raw SQL.
 *
 * None of what it proves is in the Kotlin. That the composite order is total — two consents sharing a
 * `consented_at` are separated by the primary key, so pages of one walk every row exactly once —
 * compiles either way and fails only against a database. So does the optional subject: an absent one is
 * bound as a null the driver has to type, and the `COALESCE` is what gives it a single typed usage.
 */
class ConsentRepositoryTest {

    /**
     * The audience the consents carry, and the two providers their users are linked under. Each names
     * this test class, so no other class's rows fall inside the queries below.
     */
    private val audienceId = "consent-repository-test"
    private val providerId = "consent-repository-test-provider"
    private val otherProviderId = "consent-repository-test-other-provider"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the scope array`(database: Database) = withFixture(database) {
        val consents = repository<ConsentRepository>()
        val id = saveConsent(newUser(), BASE_DATE, scopes = arrayOf("openid", "profile", "email"))

        val stored = consents.findById(id)

        assertNotNull(stored)
        assertArrayEquals(arrayOf("openid", "profile", "email"), stored!!.scopes)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips an empty scope array`(database: Database) = withFixture(database) {
        val id = saveConsent(newUser(), BASE_DATE, scopes = emptyArray())

        val stored = repository<ConsentRepository>().findById(id)

        assertNotNull(stored)
        assertArrayEquals(emptyArray<String>(), stored!!.scopes)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findActiveByAudienceId - Orders by consent date`(database: Database) = withFixture(database) {
        val audience = seedAudience()

        val page = readWholeAudience()

        assertEquals(audience.consentIds.take(2), page.take(2))
        assertEquals(audience.consentIds.subList(2, 4).toSet(), page.drop(2).toSet())
    }

    /**
     * The tiebreak is asserted as determinism rather than as a direction: the two dialects collate a `uuid`
     * as unsigned bytes and Kotlin compares it signed, so naming which of the tied pair comes first would
     * pin a collation this query does not care about. What it needs is that the pair does not swap.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findActiveByAudienceId - Breaks a tied consent date on the identifier, so two reads agree`(
        database: Database
    ) = withFixture(database) {
        seedAudience()

        assertEquals(4, readWholeAudience().size)
        assertEquals(readWholeAudience(), readWholeAudience())
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findActiveByAudienceId - Walking pages of one yields every consent exactly once`(database: Database) =
        withFixture(database) {
            seedAudience()
            val consents = repository<ConsentRepository>()

            val walked = (0 until 4).flatMap { offset ->
                consents.findActiveByAudienceId(audienceId, limit = 1, offset = offset).map { it.id!! }
            }

            assertEquals(readWholeAudience(), walked)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findActiveByAudienceId - Excludes revoked consents`(database: Database) = withFixture(database) {
        val audience = seedAudience()

        val page = repository<ConsentRepository>().findActiveByAudienceId(audienceId, limit = 10, offset = 0)

        assertFalse(page.any { it.userId == audience.userIds[4] })
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findActiveByAudienceId - Returns nothing past the last page`(database: Database) = withFixture(database) {
        seedAudience()

        val page = repository<ConsentRepository>().findActiveByAudienceId(audienceId, limit = 2, offset = 4)

        assertEquals(emptyList<UUID>(), page.map { it.id!! })
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `countActiveByAudienceId - Counts the rows the pages walk`(database: Database) = withFixture(database) {
        seedAudience()

        val count = repository<ConsentRepository>().countActiveByAudienceId(audienceId)

        assertEquals(4L, count)
        assertEquals(readWholeAudience().size.toLong(), count)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findActiveByAudienceIdAndProvider - Keeps only the users linked to the provider`(database: Database) =
        withFixture(database) {
            val audience = seedAudience()

            val page = repository<ConsentRepository>().findActiveByAudienceIdAndProvider(
                audienceId = audienceId,
                providerId = providerId,
                subject = null,
                limit = 10,
                offset = 0
            )

            assertEquals(listOf(audience.userIds[1], audience.userIds[2]), page.map { it.userId })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findActiveByAudienceIdAndProvider - Narrows to the account bearing the subject`(database: Database) =
        withFixture(database) {
            val audience = seedAudience()

            val page = repository<ConsentRepository>().findActiveByAudienceIdAndProvider(
                audienceId = audienceId,
                providerId = providerId,
                subject = "subject-2",
                limit = 10,
                offset = 0
            )

            assertEquals(listOf(audience.userIds[2]), page.map { it.userId })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findActiveByAudienceIdAndProvider - Pages the filtered rows in the same order`(database: Database) =
        withFixture(database) {
            val audience = seedAudience()
            val consents = repository<ConsentRepository>()

            val walked = (0 until 2).flatMap { offset ->
                consents.findActiveByAudienceIdAndProvider(
                    audienceId = audienceId,
                    providerId = providerId,
                    subject = null,
                    limit = 1,
                    offset = offset
                ).map { it.userId }
            }

            assertEquals(listOf(audience.userIds[1], audience.userIds[2]), walked)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `countActiveByAudienceIdAndProvider - Counts the rows the filtered pages walk`(database: Database) =
        withFixture(database) {
            seedAudience()
            val consents = repository<ConsentRepository>()

            val withoutSubject = consents.countActiveByAudienceIdAndProvider(audienceId, providerId, null)
            val withSubject = consents.countActiveByAudienceIdAndProvider(audienceId, providerId, "subject-2")

            assertEquals(2L, withoutSubject)
            assertEquals(1L, withSubject)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdAndAudienceIdAndRevokedAtIsNull - Skips a revoked consent`(database: Database) =
        withFixture(database) {
            val audience = seedAudience()
            val consents = repository<ConsentRepository>()

            val active = consents.findByUserIdAndAudienceIdAndRevokedAtIsNull(audience.userIds[0], audienceId)
            val revoked = consents.findByUserIdAndAudienceIdAndRevokedAtIsNull(audience.userIds[4], audienceId)

            assertEquals(audience.consentIds[0], active?.id)
            assertNull(revoked)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdAndRevokedAtIsNull - Returns the user's active consents`(database: Database) =
        withFixture(database) {
            val audience = seedAudience()

            val found = repository<ConsentRepository>().findByUserIdAndRevokedAtIsNull(audience.userIds[1])

            assertEquals(listOf(audience.consentIds[1]), found.map { it.id!! })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByAudienceIdAndRevokedAtIsNull - Returns every active consent of the audience`(database: Database) =
        withFixture(database) {
            val audience = seedAudience()

            val found = repository<ConsentRepository>().findByAudienceIdAndRevokedAtIsNull(audienceId)

            assertEquals(audience.consentIds.take(4).toSet(), found.map { it.id!! }.toSet())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAt - Revokes the consent and counts the row`(database: Database) = withFixture(database) {
        val consents = repository<ConsentRepository>()
        val userId = newUser()
        val revokedById = newUser()
        val id = saveConsent(userId, BASE_DATE)
        val revokedAt = BASE_DATE.plusDays(3)

        val updated = consents.updateRevokedAt(id, revokedAt, "administrator", revokedById)

        val stored = consents.findById(id)
        assertEquals(1, updated)
        assertEquals(revokedAt, stored?.revokedAt)
        assertEquals("administrator", stored?.revokedBy)
        assertEquals(revokedById, stored?.revokedById)
        assertNull(consents.findByUserIdAndAudienceIdAndRevokedAtIsNull(userId, audienceId))
    }

    /**
     * Five users, of which the first has no provider link at all, the next two are linked to [providerId]
     * under distinct subjects, and the fourth is linked to another provider. Their consents are ordered by
     * date except for the third and fourth, which share one so the identifier tiebreak is exercised, and the
     * fifth is revoked.
     */
    private suspend fun RepositoryFixture.seedAudience(): SeededAudience {
        val userIds = (0 until 5).map { newUser() }
        val consentIds = listOf(
            saveConsent(userIds[0], BASE_DATE),
            saveConsent(userIds[1], BASE_DATE.plusMinutes(1)),
            saveConsent(userIds[2], BASE_DATE.plusMinutes(2)),
            saveConsent(userIds[3], BASE_DATE.plusMinutes(2)),
            saveConsent(userIds[4], BASE_DATE.plusMinutes(3), revokedAt = BASE_DATE.plusMinutes(4))
        )
        newProviderLink(providerId, userIds[1], "subject-1")
        newProviderLink(providerId, userIds[2], "subject-2")
        newProviderLink(otherProviderId, userIds[3], "subject-3")
        newProviderLink(providerId, userIds[4], "subject-4")
        return SeededAudience(userIds, consentIds)
    }

    private class SeededAudience(val userIds: List<UUID>, val consentIds: List<UUID>)

    private suspend fun RepositoryFixture.readWholeAudience(): List<UUID> = repository<ConsentRepository>()
        .findActiveByAudienceId(audienceId, limit = 10, offset = 0)
        .map { it.id!! }

    private suspend fun RepositoryFixture.saveConsent(
        userId: UUID,
        consentedAt: LocalDateTime,
        revokedAt: LocalDateTime? = null,
        scopes: Array<String> = arrayOf("profile")
    ): UUID {
        val consents = repository<ConsentRepository>()
        return consents.save(
            ConsentEntity(
                userId = userId,
                audienceId = audienceId,
                promptedByClientId = "consent-repository-test-client",
                scopes = scopes,
                consentedAt = consentedAt,
                revokedAt = revokedAt
            )
        ).id!!.also { id -> deleteOnEnd { consents.deleteById(id) } }
    }

}

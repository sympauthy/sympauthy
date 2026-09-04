package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * The link between a user and a provider, whose key is the only [io.micronaut.data.annotation.EmbeddedId]
 * in the model — two columns the entity names through explicit [io.micronaut.data.annotation.MappedProperty]
 * rather than through the naming strategy every other entity relies on.
 */
class ProviderUserInfoRepositoryTest {

    private val providerId = "provider-user-info-repository-test"
    private val otherProviderId = "provider-user-info-repository-test-other"

    private val fetchedAt: LocalDateTime = BASE_DATE.plusMonths(2)

    @ParameterizedTest(name = "save - Round-trips every column it was given on {0}")
    @EnumSource(Database::class)
    fun `save - Round-trips every column it was given`(database: Database) = withFixture(database) {
        val userId = newUser()

        saveLink(userId, subject = "123456789012345678")

        val stored = repository<ProviderUserInfoRepository>()
            .findByProviderIdAndUserId(providerId, userId)

        assertNotNull(stored)
        assertEquals(BASE_DATE, stored!!.linkDate)
        assertEquals(BASE_DATE, stored.fetchDate)
        assertEquals(BASE_DATE, stored.changeDate)
        assertEquals("123456789012345678", stored.subject)
        assertEquals("Ada Lovelace", stored.name)
        assertEquals("ada@example.org", stored.email)
        assertEquals(true, stored.emailVerified)
        assertEquals(LocalDate.of(1815, 12, 10), stored.birthDate)
        assertEquals("Europe/London", stored.zoneInfo)
    }

    @ParameterizedTest(name = "update - Carries the link date back unchanged while the fetch date moves on {0}")
    @EnumSource(Database::class)
    fun `update - Carries the link date back unchanged while the fetch date moves`(database: Database) =
        withFixture(database) {
            val userId = newUser()
            val stored = saveLink(userId, subject = "123456789012345678")
            val links = repository<ProviderUserInfoRepository>()

            links.update(stored.copy(fetchDate = fetchedAt, changeDate = fetchedAt))

            val refreshed = links.findByProviderIdAndUserId(providerId, userId)

            assertNotNull(refreshed)
            assertEquals(BASE_DATE, refreshed!!.linkDate)
            assertEquals(fetchedAt, refreshed.fetchDate)
            assertEquals(fetchedAt, refreshed.changeDate)
        }

    @ParameterizedTest(name = "findByProviderIdAndSubject - Finds the link bearing the subject on {0}")
    @EnumSource(Database::class)
    fun `findByProviderIdAndSubject - Finds the link bearing the subject`(database: Database) =
        withFixture(database) {
            val userId = newUser()
            saveLink(userId, subject = "123456789012345678")
            val links = repository<ProviderUserInfoRepository>()

            val found = links.findByProviderIdAndSubject(providerId, "123456789012345678")

            assertEquals(userId, found?.id?.userId)
            assertNull(links.findByProviderIdAndSubject(otherProviderId, "123456789012345678"))
        }

    @ParameterizedTest(name = "findByUserId - Returns every provider the user is linked to on {0}")
    @EnumSource(Database::class)
    fun `findByUserId - Returns every provider the user is linked to`(database: Database) =
        withFixture(database) {
            val userId = newUser()
            saveLink(userId, subject = "subject-1")
            saveLink(userId, subject = "subject-2", providerId = otherProviderId)

            val found = repository<ProviderUserInfoRepository>().findByUserId(userId)

            assertEquals(setOf(providerId, otherProviderId), found.map { it.id.providerId }.toSet())
        }

    @ParameterizedTest(name = "findByUserIdInList - Returns the links of every user in the list on {0}")
    @EnumSource(Database::class)
    fun `findByUserIdInList - Returns the links of every user in the list`(database: Database) =
        withFixture(database) {
            val linked = newUser()
            val alsoLinked = newUser()
            val unlisted = newUser()
            saveLink(linked, subject = "subject-1")
            saveLink(alsoLinked, subject = "subject-2")
            saveLink(unlisted, subject = "subject-3")

            val found = repository<ProviderUserInfoRepository>().findByUserIdInList(listOf(linked, alsoLinked))

            assertEquals(setOf(linked, alsoLinked), found.map { it.id.userId }.toSet())
        }

    @ParameterizedTest(name = "deleteByProviderIdAndUserId - Removes one link and counts it on {0}")
    @EnumSource(Database::class)
    fun `deleteByProviderIdAndUserId - Removes one link and counts it`(database: Database) =
        withFixture(database) {
            val userId = newUser()
            saveLink(userId, subject = "subject-1")
            saveLink(userId, subject = "subject-2", providerId = otherProviderId)
            val links = repository<ProviderUserInfoRepository>()

            val deleted = links.deleteByProviderIdAndUserId(providerId, userId)

            assertEquals(1, deleted)
            assertNull(links.findByProviderIdAndUserId(providerId, userId))
            assertNotNull(links.findByProviderIdAndUserId(otherProviderId, userId))
        }

    private suspend fun RepositoryFixture.saveLink(
        userId: UUID,
        subject: String,
        providerId: String = this@ProviderUserInfoRepositoryTest.providerId
    ): ProviderUserInfoEntity {
        val links = repository<ProviderUserInfoRepository>()
        val id = ProviderUserInfoEntityId(providerId = providerId, userId = userId)
        return links.save(
            ProviderUserInfoEntity(
                id = id,
                linkDate = BASE_DATE,
                fetchDate = BASE_DATE,
                changeDate = BASE_DATE,
                subject = subject,
                name = "Ada Lovelace",
                email = "ada@example.org",
                emailVerified = true,
                birthDate = LocalDate.of(1815, 12, 10),
                zoneInfo = "Europe/London"
            )
        ).also { deleteOnEnd { links.deleteByProviderIdAndUserId(providerId, userId) } }
    }
}

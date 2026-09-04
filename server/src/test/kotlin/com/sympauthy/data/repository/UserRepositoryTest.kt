package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.withFixture
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The user, whose reader by status is the one query in the model that is not `suspend`: it hands back a
 * [kotlinx.coroutines.flow.Flow] the caller collects itself.
 */
class UserRepositoryTest {

    private val status = "user-repository-test-enabled"
    private val otherStatus = "user-repository-test-locked"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Generates the identifier and round-trips the row`(database: Database) = withFixture(database) {
        val users = repository<UserRepository>()
        val id = newUser(status = status)

        val stored = users.findById(id)

        assertNotNull(stored)
        assertEquals(id, stored!!.id)
        assertEquals(status, stored.status)
        assertEquals(BASE_DATE, stored.creationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByStatus - Streams the users holding the status`(database: Database) = withFixture(database) {
        val users = repository<UserRepository>()
        val first = newUser(status = status)
        val second = newUser(status = status)
        newUser(status = otherStatus)

        val found = users.findByStatus(status).toList().map { it.id!! }

        assertEquals(setOf(first, second), found.toSet())
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByStatus - Streams nothing when no user holds the status`(database: Database) =
        withFixture(database) {
            newUser(status = status)

            val found = repository<UserRepository>().findByStatus("user-repository-test-absent").toList()

            assertTrue(found.isEmpty())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdInList - Returns every user in the list`(database: Database) = withFixture(database) {
        val users = repository<UserRepository>()
        val first = newUser(status = status)
        val second = newUser(status = status)
        val unlisted = newUser(status = status)

        val found = users.findByIdInList(listOf(first, second)).map { it.id!! }

        assertEquals(setOf(first, second), found.toSet())
        assertTrue(!found.contains(unlisted))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdInList - Returns nothing when the list is empty`(database: Database) = withFixture(database) {
        newUser(status = status)

        assertTrue(repository<UserRepository>().findByIdInList(emptyList()).isEmpty())
    }
}

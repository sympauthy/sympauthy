package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.model.PasswordEntity
import com.sympauthy.data.model.TotpEnrollmentEntity
import com.sympauthy.data.withFixture
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The user, whose reader by status is the one query in the model that is not `suspend`: it hands back a
 * [kotlinx.coroutines.flow.Flow] the caller collects itself.
 *
 * It is also where the provisional row is proved invisible: every reader here answers committed rows only,
 * except the one the owning session reads its own account through.
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
        assertNull(stored.sessionId)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the session a provisional row belongs to`(database: Database) =
        withFixture(database) {
            val session = newSession()
            val id = newUser(status = status, sessionId = session.id)

            assertEquals(session.id, repository<UserRepository>().findById(id)?.sessionId)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByStatusAndSessionIdIsNull - Streams the committed users holding the status`(database: Database) =
        withFixture(database) {
            val users = repository<UserRepository>()
            val session = newSession()
            val first = newUser(status = status)
            val second = newUser(status = status)
            newUser(status = otherStatus)
            newUser(status = status, sessionId = session.id)

            val found = users.findByStatusAndSessionIdIsNull(status).toList().map { it.id!! }

            assertEquals(setOf(first, second), found.toSet())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByStatusAndSessionIdIsNull - Streams nothing when no user holds the status`(database: Database) =
        withFixture(database) {
            newUser(status = status)

            val found = repository<UserRepository>()
                .findByStatusAndSessionIdIsNull("user-repository-test-absent")
                .toList()

            assertTrue(found.isEmpty())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionIdIsNull - Excludes the users a session is still signing up`(database: Database) =
        withFixture(database) {
            val session = newSession()
            val committed = newUser(status = status)
            val provisional = newUser(status = status, sessionId = session.id)

            val found = repository<UserRepository>().findBySessionIdIsNull().toList().map { it.id!! }

            assertTrue(found.contains(committed))
            assertTrue(!found.contains(provisional))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdAndSessionIdIsNull - Returns the user once it is committed and not before`(database: Database) =
        withFixture(database) {
            val users = repository<UserRepository>()
            val session = newSession()
            val id = newUser(status = status, sessionId = session.id)

            assertNull(users.findByIdAndSessionIdIsNull(id))

            users.clearSessionId(id, session.id!!)

            assertEquals(id, users.findByIdAndSessionIdIsNull(id)?.id)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdInListAndSessionIdIsNull - Returns every committed user in the list`(database: Database) =
        withFixture(database) {
            val users = repository<UserRepository>()
            val session = newSession()
            val first = newUser(status = status)
            val second = newUser(status = status)
            val unlisted = newUser(status = status)
            val provisional = newUser(status = status, sessionId = session.id)

            val found = users
                .findByIdInListAndSessionIdIsNull(listOf(first, second, provisional))
                .map { it.id!! }

            assertEquals(setOf(first, second), found.toSet())
            assertTrue(!found.contains(unlisted))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdInListAndSessionIdIsNull - Returns nothing when the list is empty`(database: Database) =
        withFixture(database) {
            newUser(status = status)

            assertTrue(repository<UserRepository>().findByIdInListAndSessionIdIsNull(emptyList()).isEmpty())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdVisibleInSession - Returns the account the session is signing up`(database: Database) =
        withFixture(database) {
            val session = newSession()
            val id = newUser(status = status, sessionId = session.id)

            assertEquals(id, repository<UserRepository>().findByIdVisibleInSession(id, session.id!!)?.id)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdVisibleInSession - Returns a committed account to any session`(database: Database) =
        withFixture(database) {
            val session = newSession()
            val id = newUser(status = status)

            assertEquals(id, repository<UserRepository>().findByIdVisibleInSession(id, session.id!!)?.id)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdVisibleInSession - Hides the account another session is signing up`(database: Database) =
        withFixture(database) {
            val owning = newSession()
            val other = newSession()
            val id = newUser(status = status, sessionId = owning.id)

            assertNull(repository<UserRepository>().findByIdVisibleInSession(id, other.id!!))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByIdAndSessionId - Answers only the account that session made provisional`(database: Database) =
        withFixture(database) {
            val users = repository<UserRepository>()
            val session = newSession()
            val provisional = newUser(status = status, sessionId = session.id)
            val committed = newUser(status = status)

            assertEquals(provisional, users.findByIdAndSessionId(provisional, session.id!!)?.id)
            assertNull(users.findByIdAndSessionId(committed, session.id!!))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findAbandoned - Finds the account whose session is gone`(database: Database) = withFixture(database) {
        val users = repository<UserRepository>()
        val session = newSession()
        val ongoing = newSession()
        val abandoned = newUser(status = status, sessionId = session.id)
        val stillSigningUp = newUser(status = status, sessionId = ongoing.id)
        val committed = newUser(status = status)
        repository<InteractiveFlowSessionRepository>().deleteByIds(listOf(session.id!!))

        val found = users.findAbandoned().map { it.id!! }

        assertTrue(found.contains(abandoned))
        assertTrue(!found.contains(stillSigningUp))
        assertTrue(!found.contains(committed))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findAbandoned - Skips an account a session still refers to`(database: Database) =
        withFixture(database) {
            val users = repository<UserRepository>()
            val session = newSession()
            val userId = newUser(status = status, sessionId = session.id)
            newSession(userId = userId)
            repository<InteractiveFlowSessionRepository>().deleteByIds(listOf(session.id!!))

            assertTrue(!users.findAbandoned().map { it.id!! }.contains(userId))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `clearSessionId - Promotes that account of that session and no other`(database: Database) =
        withFixture(database) {
            val users = repository<UserRepository>()
            val session = newSession()
            val otherSession = newSession()
            val promoted = newUser(status = status, sessionId = session.id)
            // A second account of the same session: only the one named is promoted, so an account the
            // uniqueness re-check never saw cannot ride along with the one it did.
            val sibling = newUser(status = status, sessionId = session.id)
            val untouched = newUser(status = status, sessionId = otherSession.id)

            assertEquals(1, users.clearSessionId(promoted, session.id!!))

            assertNull(users.findById(promoted)?.sessionId)
            assertEquals(session.id, users.findById(sibling)?.sessionId)
            assertEquals(otherSession.id, users.findById(untouched)?.sessionId)
        }

    /**
     * The ordering the cleaner deletes in, against a real database, because a foreign key it broke would
     * abort the whole run rather than leave one row behind — and would do so again every quarter of an hour.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `Every row an abandoned account owns is deleted before the account`(database: Database) =
        withFixture(database) {
            val users = repository<UserRepository>()
            val session = newSession()
            val userId = newUser(status = status, sessionId = session.id)
            repository<PasswordRepository>().save(
                PasswordEntity(
                    userId = userId,
                    salt = byteArrayOf(1),
                    hashedPassword = byteArrayOf(2),
                    creationDate = BASE_DATE,
                    expirationDate = null,
                    sessionId = session.id
                )
            )
            repository<CollectedClaimRepository>().save(
                CollectedClaimEntity(
                    userId = userId,
                    claim = "email",
                    value = "\"abandoned@$status.test\"",
                    verified = null,
                    collectionDate = BASE_DATE,
                    verificationDate = null,
                    sessionId = session.id
                )
            )
            newProviderLink("provider-$status", userId, "subject-$status", sessionId = session.id)
            repository<TotpEnrollmentRepository>().save(
                TotpEnrollmentEntity(
                    userId = userId,
                    secret = byteArrayOf(3),
                    creationDate = BASE_DATE,
                    confirmedDate = null,
                    sessionId = session.id
                )
            )
            repository<InteractiveFlowSessionRepository>().deleteByIds(listOf(session.id!!))

            val abandoned = users.findAbandoned().mapNotNull { it.id }
            repository<PasswordRepository>().deleteByUserIdIn(abandoned)
            repository<CollectedClaimRepository>().deleteByUserIdIn(abandoned)
            repository<ProviderUserInfoRepository>().deleteByUserIdIn(abandoned)
            repository<TotpEnrollmentRepository>().deleteByUserIdIn(abandoned)

            assertEquals(1, users.deleteByIdIn(listOf(userId)))
            assertNull(users.findById(userId))
        }
}

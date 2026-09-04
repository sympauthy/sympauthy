package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.AuthenticationTokenEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The three scope arrays a token carries, and the five derived updates that revoke one — each named for
 * the column it sets and reached by a different key.
 */
class AuthenticationTokenRepositoryTest {

    private val clientId = "authentication-token-repository-test-client"
    private val otherClientId = "authentication-token-repository-test-other-client"
    private val revokedAt = BASE_DATE.plusHours(1)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the three scope arrays`(database: Database) = withFixture(database) {
        val tokens = repository<AuthenticationTokenRepository>()
        val id = saveToken(
            newUser(),
            grantedScopes = arrayOf("admin"),
            consentedScopes = arrayOf("openid", "profile"),
            clientScopes = emptyArray()
        )

        val stored = tokens.findById(id)

        assertNotNull(stored)
        assertArrayEquals(arrayOf("admin"), stored!!.grantedScopes)
        assertArrayEquals(arrayOf("openid", "profile"), stored.consentedScopes)
        assertArrayEquals(emptyArray<String>(), stored.clientScopes)
        assertEquals("authorization_code", stored.grantType)
        assertEquals(BASE_DATE, stored.issueDate)
    }

    /** A client-credentials token carries neither a user nor a session, and both columns admit null. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips a token bound to no user and no session`(database: Database) = withFixture(database) {
        val tokens = repository<AuthenticationTokenRepository>()
        val id = saveToken(userId = null, sessionId = null, grantType = "client_credentials")

        val stored = tokens.findById(id)

        assertNotNull(stored)
        assertNull(stored!!.userId)
        assertNull(stored.sessionId)
        assertNull(stored.expirationDate)
        assertNull(stored.revokedAt)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAt - Revokes the token it names`(database: Database) = withFixture(database) {
        val tokens = repository<AuthenticationTokenRepository>()
        val userId = newUser()
        val id = saveToken(userId)
        val untouched = saveToken(userId)

        tokens.updateRevokedAt(id, revokedAt, "administrator", userId)

        assertEquals(revokedAt, tokens.findById(id)?.revokedAt)
        assertEquals("administrator", tokens.findById(id)?.revokedBy)
        assertEquals(userId, tokens.findById(id)?.revokedById)
        assertNull(tokens.findById(untouched)!!.revokedAt)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtBySessionId - Revokes every token of the session`(database: Database) =
        withFixture(database) {
            val tokens = repository<AuthenticationTokenRepository>()
            val userId = newUser()
            val sessionId = UUID.randomUUID()
            val first = saveToken(userId, sessionId = sessionId)
            val second = saveToken(userId, sessionId = sessionId)
            val other = saveToken(userId, sessionId = UUID.randomUUID())

            tokens.updateRevokedAtBySessionId(sessionId, revokedAt, "administrator", null)

            assertEquals(revokedAt, tokens.findById(first)?.revokedAt)
            assertEquals(revokedAt, tokens.findById(second)?.revokedAt)
            assertNull(tokens.findById(other)!!.revokedAt)
            assertNull(tokens.findById(first)!!.revokedById)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtByActorTokenId - Revokes the derived tokens and counts them`(database: Database) =
        withFixture(database) {
            val tokens = repository<AuthenticationTokenRepository>()
            val userId = newUser()
            val actorTokenId = saveToken(userId)
            val derived = saveToken(userId, actorTokenId = actorTokenId)
            val unrelated = saveToken(userId)

            val count = tokens.updateRevokedAtByActorTokenId(actorTokenId, revokedAt, "administrator", userId)

            assertEquals(1, count)
            assertEquals(revokedAt, tokens.findById(derived)?.revokedAt)
            assertNull(tokens.findById(unrelated)!!.revokedAt)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtByUserIdAndClientId - Revokes on both keys at once`(database: Database) =
        withFixture(database) {
            val tokens = repository<AuthenticationTokenRepository>()
            val userId = newUser()
            val otherUserId = newUser()
            val revoked = saveToken(userId, clientId = clientId)
            val otherClient = saveToken(userId, clientId = otherClientId)
            val otherUser = saveToken(otherUserId, clientId = clientId)

            val count = tokens.updateRevokedAtByUserIdAndClientId(
                userId, clientId, revokedAt, "administrator", userId
            )

            assertEquals(1, count)
            assertEquals(revokedAt, tokens.findById(revoked)?.revokedAt)
            assertNull(tokens.findById(otherClient)!!.revokedAt)
            assertNull(tokens.findById(otherUser)!!.revokedAt)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtByUserId - Revokes every token of the user`(database: Database) = withFixture(database) {
        val tokens = repository<AuthenticationTokenRepository>()
        val userId = newUser()
        val otherUserId = newUser()
        val first = saveToken(userId, clientId = clientId)
        val second = saveToken(userId, clientId = otherClientId)
        val other = saveToken(otherUserId)

        val count = tokens.updateRevokedAtByUserId(userId, revokedAt, "administrator", userId)

        assertEquals(2, count)
        assertEquals(revokedAt, tokens.findById(first)?.revokedAt)
        assertEquals(revokedAt, tokens.findById(second)?.revokedAt)
        assertNull(tokens.findById(other)!!.revokedAt)
    }

    private suspend fun RepositoryFixture.saveToken(
        userId: UUID?,
        clientId: String = this@AuthenticationTokenRepositoryTest.clientId,
        sessionId: UUID? = UUID.randomUUID(),
        grantType: String = "authorization_code",
        actorTokenId: UUID? = null,
        grantedScopes: Array<String> = emptyArray(),
        consentedScopes: Array<String> = arrayOf("openid"),
        clientScopes: Array<String> = emptyArray()
    ): UUID {
        val tokens = repository<AuthenticationTokenRepository>()
        return tokens.save(
            AuthenticationTokenEntity(
                type = "access",
                userId = userId,
                clientId = clientId,
                grantedScopes = grantedScopes,
                consentedScopes = consentedScopes,
                clientScopes = clientScopes,
                sessionId = sessionId,
                grantType = grantType,
                actorTokenId = actorTokenId,
                issueDate = BASE_DATE,
                expirationDate = null
            )
        ).id!!.also { id -> deleteOnEnd { tokens.deleteById(id) } }
    }
}

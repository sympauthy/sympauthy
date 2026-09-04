package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.InteractiveFlowSessionOAuth2Entity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The authorize request a session was opened for. Its key is the session's own identifier rather than a
 * generated one, so an insert has to carry it, and its two scope updates are handed a `List` for a
 * column the entity reads back as an `Array`.
 */
class InteractiveFlowSessionOAuth2RepositoryTest {

    private val clientId = "interactive-flow-session-oauth2-repository-test-client"

    @ParameterizedTest(name = "save - Carries the assigned key and round-trips the scope arrays on {0}")
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and round-trips the scope arrays`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionOAuth2Repository>()
            val session = newSession()

            saveRecord(session.id!!, requestedScopes = arrayOf("openid", "profile"))

            val stored = records.findById(session.id!!)
            assertNotNull(stored)
            assertEquals(session.id, stored!!.sessionId)
            assertArrayEquals(arrayOf("openid", "profile"), stored.requestedScopes)
            assertEquals(clientId, stored.clientId)
            assertEquals("S256", stored.codeChallengeMethod)
        }

    /** A failed authorize request keeps its state without a client, a redirect uri or any scope decision. */
    @ParameterizedTest(name = "save - Round-trips a record whose optional columns are all absent on {0}")
    @EnumSource(Database::class)
    fun `save - Round-trips a record whose optional columns are all absent`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionOAuth2Repository>()
            val session = newSession()

            records.save(InteractiveFlowSessionOAuth2Entity(sessionId = session.id!!))
            deleteOnEnd { records.deleteBySessionIdIn(listOf(session.id!!)) }

            val stored = records.findById(session.id!!)
            assertNotNull(stored)
            assertNull(stored!!.clientId)
            assertNull(stored.redirectUri)
            assertNull(stored.consentedScopes)
            assertNull(stored.grantedScopes)
            assertNull(stored.invitationId)
            assertArrayEquals(emptyArray<String>(), stored.requestedScopes)
        }

    @ParameterizedTest(name = "findBySessionId - Finds the record of the session on {0}")
    @EnumSource(Database::class)
    fun `findBySessionId - Finds the record of the session`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionOAuth2Repository>()
        val session = newSession()
        val other = newSession()
        saveRecord(session.id!!)

        assertEquals(session.id, records.findBySessionId(session.id!!)?.sessionId)
        assertNull(records.findBySessionId(other.id!!))
    }

    @ParameterizedTest(name = "findByState - Finds the record bearing the state on {0}")
    @EnumSource(Database::class)
    fun `findByState - Finds the record bearing the state`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionOAuth2Repository>()
        val session = newSession()
        saveRecord(session.id!!, state = "interactive-flow-session-oauth2-repository-test-state")

        val found = records.findByState("interactive-flow-session-oauth2-repository-test-state")

        assertEquals(session.id, found?.sessionId)
        assertNull(records.findByState("interactive-flow-session-oauth2-repository-test-absent"))
    }

    @ParameterizedTest(name = "updateConsentedScopes - Writes the list into the array column on {0}")
    @EnumSource(Database::class)
    fun `updateConsentedScopes - Writes the list into the array column`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionOAuth2Repository>()
            val session = newSession()
            saveRecord(session.id!!)
            val consentedAt = BASE_DATE.plusMinutes(1)

            records.updateConsentedScopes(session.id!!, listOf("openid", "email"), consentedAt, "end-user")

            val stored = records.findById(session.id!!)!!
            assertArrayEquals(arrayOf("openid", "email"), stored.consentedScopes)
            assertEquals(consentedAt, stored.consentedAt)
            assertEquals("end-user", stored.consentedBy)
            assertNull(stored.grantedScopes)
        }

    @ParameterizedTest(name = "updateGrantedScopes - Writes the list into the array column on {0}")
    @EnumSource(Database::class)
    fun `updateGrantedScopes - Writes the list into the array column`(database: Database) =
        withFixture(database) {
            val records = repository<InteractiveFlowSessionOAuth2Repository>()
            val session = newSession()
            saveRecord(session.id!!)
            val grantedAt = BASE_DATE.plusMinutes(2)

            records.updateGrantedScopes(session.id!!, listOf("admin"), grantedAt, "rule")

            val stored = records.findById(session.id!!)!!
            assertArrayEquals(arrayOf("admin"), stored.grantedScopes)
            assertEquals(grantedAt, stored.grantedAt)
            assertEquals("rule", stored.grantedBy)
        }

    @ParameterizedTest(name = "deleteBySessionIdIn - Removes the records and counts them on {0}")
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the records and counts them`(database: Database) = withFixture(database) {
        val records = repository<InteractiveFlowSessionOAuth2Repository>()
        val deleted = newSession()
        val kept = newSession()
        saveRecord(deleted.id!!)
        saveRecord(kept.id!!)

        val count = records.deleteBySessionIdIn(listOf(deleted.id!!))

        assertEquals(1, count)
        assertNull(records.findBySessionId(deleted.id!!))
        assertNotNull(records.findBySessionId(kept.id!!))
    }

    private suspend fun RepositoryFixture.saveRecord(
        sessionId: UUID,
        requestedScopes: Array<String> = arrayOf("openid"),
        state: String? = null
    ) {
        val records = repository<InteractiveFlowSessionOAuth2Repository>()
        records.save(
            InteractiveFlowSessionOAuth2Entity(
                sessionId = sessionId,
                clientId = clientId,
                redirectUri = "https://example.org/callback",
                requestedScopes = requestedScopes,
                state = state,
                nonce = "nonce",
                codeChallenge = "challenge",
                codeChallengeMethod = "S256"
            )
        )
        deleteOnEnd { records.deleteBySessionIdIn(listOf(sessionId)) }
    }
}

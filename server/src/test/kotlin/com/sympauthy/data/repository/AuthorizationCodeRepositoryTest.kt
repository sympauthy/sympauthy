package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.bean
import com.sympauthy.data.model.AuthorizationCodeEntity
import com.sympauthy.data.withFixture
import com.sympauthy.util.isRowAlreadyThere
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The code handed back from an authorize request, keyed by the session it belongs to rather than by an
 * identifier of its own, so the insert has to carry the key it was given.
 *
 * The exchange is the one write here two requests race over, so the case proving it holds its first
 * writer over a connection of its own, with the transaction left open while the second tries.
 */
class AuthorizationCodeRepositoryTest {

    private val code = "authorization-code-repository-test-code"
    private val otherCode = "authorization-code-repository-test-other-code"

    /**
     * Long enough that an update returning inside it never waited, and well under the two seconds H2
     * waits for a row before giving up.
     */
    private val hold = 400L

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Carries the assigned key and round-trips the row`(database: Database) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val session = newSession()

        newCode(session.id!!, code)

        val stored = codes.findById(session.id!!)
        assertNotNull(stored)
        assertEquals(session.id, stored!!.sessionId)
        assertEquals(code, stored.code)
        assertEquals(BASE_DATE, stored.creationDate)
        assertEquals(BASE_DATE.plusMinutes(10), stored.expirationDate)
        assertNull(stored.exchangeDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateExchangeDate - Writes the date once, and on the code of that session alone`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val session = newSession()
            val other = newSession()
            newCode(session.id!!, code)
            newCode(other.id!!, otherCode)

            assertEquals(1, codes.updateExchangeDate(session.id!!, BASE_DATE))
            assertEquals(0, codes.updateExchangeDate(session.id!!, BASE_DATE.plusMinutes(1)))

            assertEquals(BASE_DATE, codes.findById(session.id!!)!!.exchangeDate)
            assertNull(codes.findById(other.id!!)!!.exchangeDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateExchangeDate - Waits for the exchange holding the code, and reads the date it committed`(
        database: Database
    ) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val session = newSession()
        val other = newSession()
        newCode(session.id!!, code)
        newCode(other.id!!, otherCode)
        val holder = database.bean<ConnectionFactory>().create().awaitFirst()

        try {
            holder.exchange(session.id!!)

            coroutineScope {
                val waiting = async(Dispatchers.IO) { codes.updateExchangeDate(session.id!!, BASE_DATE) }
                val elsewhere = async(Dispatchers.IO) { codes.updateExchangeDate(other.id!!, BASE_DATE) }
                withContext(Dispatchers.IO) { delay(hold) }

                assertFalse(waiting.isCompleted, "The code was written while another exchange held it.")
                assertTrue(elsewhere.isCompleted, "A free code waited, so the wait was not about the code.")

                holder.commitTransaction().awaitFirstOrNull()
                assertEquals(0, waiting.await(), "The code was exchanged a second time.")
            }
        } finally {
            holder.close().awaitFirstOrNull()
        }
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteBySessionIdIn - Removes the codes of every session and counts them`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val deleted = newSession()
            val alsoDeleted = newSession()
            val kept = newSession()
            newCode(deleted.id!!, code)
            newCode(alsoDeleted.id!!, otherCode)
            newCode(kept.id!!, "authorization-code-repository-test-kept-code")

            val count = codes.deleteBySessionIdIn(listOf(deleted.id!!, alsoDeleted.id!!))

            assertEquals(2, count)
            assertNull(codes.findById(deleted.id!!))
            assertNull(codes.findById(alsoDeleted.id!!))
            assertNotNull(codes.findById(kept.id!!))
        }

    /**
     * A departure from leaving a constraint to the database: `AuthorizationCodeManager` reads this refusal
     * as the session having already taken its code and answers the replay with it, so a dialect refusing it
     * as something else leaves that branch unreachable and answers a replay with a `500`.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Refuses a second code for one session, on either dialect`(database: Database) =
        withFixture(database) {
            val session = newSession()
            newCode(session.id!!, code)

            val refusal = assertThrows<Exception> { newCode(session.id!!, otherCode) }

            assertTrue(refusal.isRowAlreadyThere(), "The refusal does not read as the key being taken.")
        }

    /**
     * The other way the write is refused, and the reason that branch reads the refusal rather than the
     * driver's whole class of integrity failures: a session that is gone is this server's own failure.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Refuses a code for a session that is not there, as something else`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val entity = AuthorizationCodeEntity(
                sessionId = UUID.randomUUID(),
                code = code,
                creationDate = BASE_DATE,
                expirationDate = BASE_DATE.plusMinutes(10)
            )

            val refusal = assertThrows<Exception> { codes.save(entity) }

            assertFalse(refusal.isRowAlreadyThere(), "A session that is gone reads as the key being taken.")
        }

    /** Opens a transaction on this connection and exchanges the code of [sessionId] in it, leaving both open. */
    private suspend fun Connection.exchange(sessionId: UUID) {
        beginTransaction().awaitFirstOrNull()
        createStatement(
            "UPDATE authorization_codes SET exchange_date = LOCALTIMESTAMP WHERE session_id = '$sessionId'"
        )
            .execute()
            .awaitFirst()
            .rowsUpdated
            .awaitFirst()
    }
}

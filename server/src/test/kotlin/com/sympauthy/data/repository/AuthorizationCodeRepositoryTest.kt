package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.bean
import com.sympauthy.data.withFixture
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The code handed back from an authorize request, keyed by the grant it was issued under rather than by an
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
        val grantId = newGrant(newUser())

        newCode(grantId, code)

        val stored = codes.findById(grantId)
        assertNotNull(stored)
        assertEquals(grantId, stored!!.grantId)
        assertEquals(code, stored.code)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", stored.codeChallenge)
        assertEquals("S256", stored.codeChallengeMethod)
        assertNull(stored.nonce)
        assertEquals(BASE_DATE, stored.creationDate)
        assertEquals(BASE_DATE.plusMinutes(10), stored.expirationDate)
        assertNull(stored.exchangeDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByCode - Answers the code holding that value`(database: Database) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val grantId = newGrant(newUser())
        newCode(grantId, code)
        newCode(newGrant(newUser()), otherCode)

        assertEquals(grantId, codes.findByCode(code)?.grantId)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByCode - Answers null when no code holds that value`(database: Database) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        newCode(newGrant(newUser()), code)

        assertNull(codes.findByCode("authorization-code-repository-test-absent"))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Answers the code of the grant the session opened`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val sessionId = UUID.randomUUID()
            val grantId = newGrant(newUser(), sessionId = sessionId)
            newCode(grantId, code)
            newCode(newGrant(newUser()), otherCode)

            assertEquals(code, codes.findBySessionId(sessionId)?.code)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findBySessionId - Answers null for a session whose grant holds no code`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val sessionId = UUID.randomUUID()
            newGrant(newUser(), sessionId = sessionId)

            assertNull(codes.findBySessionId(sessionId))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateExchangeDate - Writes the date once, and on the code of that grant alone`(database: Database) =
        withFixture(database) {
            val codes = repository<AuthorizationCodeRepository>()
            val grantId = newGrant(newUser())
            val otherGrantId = newGrant(newUser())
            newCode(grantId, code)
            newCode(otherGrantId, otherCode)

            assertEquals(1, codes.updateExchangeDate(grantId, BASE_DATE))
            assertEquals(0, codes.updateExchangeDate(grantId, BASE_DATE.plusMinutes(1)))

            assertEquals(BASE_DATE, codes.findById(grantId)!!.exchangeDate)
            assertNull(codes.findById(otherGrantId)!!.exchangeDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateExchangeDate - Waits for the exchange holding the code, and reads the date it committed`(
        database: Database
    ) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val grantId = newGrant(newUser())
        val otherGrantId = newGrant(newUser())
        newCode(grantId, code)
        newCode(otherGrantId, otherCode)
        val holder = database.bean<ConnectionFactory>().create().awaitFirst()

        try {
            holder.exchange(grantId)

            coroutineScope {
                val waiting = async(Dispatchers.IO) { codes.updateExchangeDate(grantId, BASE_DATE) }
                val elsewhere = async(Dispatchers.IO) { codes.updateExchangeDate(otherGrantId, BASE_DATE) }
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
    fun `deleteByExpirationDateLessThan - Removes the codes expired before it, exchanged or not`(
        database: Database
    ) = withFixture(database) {
        val codes = repository<AuthorizationCodeRepository>()
        val expired = newGrant(newUser())
        val exchanged = newGrant(newUser())
        val kept = newGrant(newUser())
        newCode(expired, code, expirationDate = BASE_DATE.minusMinutes(1))
        newCode(
            exchanged, otherCode,
            expirationDate = BASE_DATE.minusMinutes(1), exchangeDate = BASE_DATE.minusMinutes(2)
        )
        newCode(kept, "authorization-code-repository-test-kept-code", expirationDate = BASE_DATE)

        val count = codes.deleteByExpirationDateLessThan(BASE_DATE)

        assertEquals(2, count)
        assertNull(codes.findById(expired))
        assertNull(codes.findById(exchanged))
        assertNotNull(codes.findById(kept))
    }

    /** Opens a transaction on this connection and exchanges the code of [grantId] in it, leaving both open. */
    private suspend fun Connection.exchange(grantId: UUID) {
        beginTransaction().awaitFirstOrNull()
        createStatement(
            "UPDATE authorization_codes SET exchange_date = LOCALTIMESTAMP WHERE grant_id = '$grantId'"
        )
            .execute()
            .awaitFirst()
            .rowsUpdated
            .awaitFirst()
    }
}

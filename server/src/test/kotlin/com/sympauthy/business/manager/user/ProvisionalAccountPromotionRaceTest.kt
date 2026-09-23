package com.sympauthy.business.manager.user

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.lock.LockKey
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * That two flows promoting one identity take turns, and that the second of them refuses.
 *
 * The winner is driven by hand over a connection of its own — its lock taken, its rows made committed, its
 * transaction left open — because nothing else proves the serialisation. Two promotions racing freely assert
 * a property that held before any lock existed: whichever ran second would find the first committed and
 * refuse. What has to be seen is the loser reaching the lock, waiting there, and answering against what the
 * winner committed once it lets go.
 *
 * Both halves run against every dialect, which is the point of the identity being a key rather than a
 * constraint: the unique index over a provider subject holds on PostgreSQL alone.
 */
class ProvisionalAccountPromotionRaceTest {

    /** The address both accounts are signing up with, and the value the key over it folds. */
    private val email = "race@example.com"

    private val providerId = "provider-promotion-race"
    private val subject = "subject-promotion-race"

    /**
     * Long enough that a loser returning inside it never waited, and well under the two seconds H2 waits for
     * a row before giving up.
     */
    private val hold = 400L

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `promote - Waits for the promotion holding the identifier claim, then refuses`(database: Database) =
        withFixture(database) {
            val manager = database.bean<ProvisionalAccountManager>()
            val winnerSession = newSession().id!!
            val winnerId = newUser(sessionId = winnerSession)
            newClaim(winnerId, "email", email, sessionId = winnerSession)
            val loserSession = newSession().id!!
            val loserId = newUser(sessionId = loserSession)
            newClaim(loserId, "email", email, sessionId = loserSession)

            val winner = database.bean<ConnectionFactory>().create().awaitFirst()
            try {
                winner.hold(LockKey.IdentifierValue(email).stripe)
                winner.run("UPDATE collected_claims SET session_id = NULL WHERE user_id = '$winnerId'")

                coroutineScope {
                    val loser = async(Dispatchers.IO) { runCatching { manager.promote(loserSession, loserId) } }
                    withContext(Dispatchers.IO) { delay(hold) }

                    assertFalse(loser.isCompleted, "The promotion did not wait for the one holding the value.")

                    winner.commitTransaction().awaitFirstOrNull()
                    val refusal = loser.await().exceptionOrNull()

                    assertInstanceOf(BusinessException::class.java, refusal)
                    assertEquals("user.promote.identifier_taken", (refusal as BusinessException).detailsId)
                }
            } finally {
                winner.close().awaitFirstOrNull()
            }
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `promote - Waits for the promotion holding the provider identity, then refuses`(database: Database) =
        withFixture(database) {
            val manager = database.bean<ProvisionalAccountManager>()
            val winnerSession = newSession().id!!
            val winnerId = newUser(sessionId = winnerSession)
            newProviderLink(providerId, winnerId, subject, sessionId = winnerSession)
            val loserSession = newSession().id!!
            val loserId = newUser(sessionId = loserSession)
            newProviderLink(providerId, loserId, subject, sessionId = loserSession)

            val winner = database.bean<ConnectionFactory>().create().awaitFirst()
            try {
                winner.hold(LockKey.ProviderSubject(providerId, subject).stripe)
                winner.run("UPDATE provider_user_info SET session_id = NULL WHERE user_id = '$winnerId'")

                coroutineScope {
                    val loser = async(Dispatchers.IO) { runCatching { manager.promote(loserSession, loserId) } }
                    withContext(Dispatchers.IO) { delay(hold) }

                    assertFalse(loser.isCompleted, "The promotion did not wait for the one holding the subject.")

                    winner.commitTransaction().awaitFirstOrNull()
                    val refusal = loser.await().exceptionOrNull()

                    assertInstanceOf(BusinessException::class.java, refusal)
                    assertEquals("user.promote.provider_subject_taken", (refusal as BusinessException).detailsId)
                    assertEquals(providerId, (refusal as BusinessException).values["providerId"])
                }
            } finally {
                winner.close().awaitFirstOrNull()
            }
        }

    /** Opens a transaction on this connection and takes [stripe] in it, leaving both open. */
    private suspend fun Connection.hold(stripe: Int) {
        beginTransaction().awaitFirstOrNull()
        createStatement("SELECT id FROM object_locks WHERE id = $stripe FOR UPDATE")
            .execute()
            .awaitFirst()
            .map { row, _ -> row.get(0) }
            .awaitFirstOrNull()
    }

    /** Runs [sql] in the transaction this connection holds, which stays open. */
    private suspend fun Connection.run(sql: String) {
        createStatement(sql).execute().awaitFirst().rowsUpdated.awaitFirstOrNull()
    }
}

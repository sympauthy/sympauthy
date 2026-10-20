package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.withFixture
import com.sympauthy.util.isRowAlreadyThere
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The arrays a grant carries, the five updates that revoke one — each reached by a different key, and each
 * leaving a grant already revoked or lapsed as it was — and the delete collecting the finished ones.
 */
class GrantRepositoryTest {

    private val clientId = "grant-repository-test-client"
    private val otherClientId = "grant-repository-test-other-client"
    private val audienceId = "grant-repository-test-audience"
    private val otherAudienceId = "grant-repository-test-other-audience"
    private val earlierRevokedAt = BASE_DATE.plusMinutes(30)
    private val revokedAt = BASE_DATE.plusHours(1)
    private val liveUntil = revokedAt.plusHours(1)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the three scope arrays`(database: Database) = withFixture(database) {
        val grants = repository<GrantRepository>()
        val id = newGrant(
            newUser(),
            grantedScopes = arrayOf("admin"),
            consentedScopes = arrayOf("openid", "profile"),
            clientScopes = emptyArray()
        )

        val stored = grants.findById(id)

        assertNotNull(stored)
        assertArrayEquals(arrayOf("admin"), stored!!.grantedScopes)
        assertArrayEquals(arrayOf("openid", "profile"), stored.consentedScopes)
        assertArrayEquals(emptyArray<String>(), stored.clientScopes)
        assertEquals("authorization_code", stored.grantType)
        assertEquals(BASE_DATE, stored.creationDate)
    }

    /** A client-credentials grant carries neither a user nor a session, and both columns admit null. */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips a grant bound to no user and no session`(database: Database) = withFixture(database) {
        val grants = repository<GrantRepository>()
        val id = newGrant(userId = null, sessionId = null, grantType = "client_credentials")

        val stored = grants.findById(id)

        assertNotNull(stored)
        assertNull(stored!!.userId)
        assertNull(stored.sessionId)
        assertNull(stored.actorGrantId)
        assertNull(stored.expirationDate)
        assertNull(stored.revokedAt)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the authentication date, the asked-for claims and the lifetime`(database: Database) =
        withFixture(database) {
            val grants = repository<GrantRepository>()
            val authenticationDate = BASE_DATE.minusMinutes(2)
            val id = newGrant(
                newUser(),
                authenticationDate = authenticationDate,
                requestedIdTokenClaims = arrayOf("loyalty_tier"),
                requestedUserinfoClaims = arrayOf("shoe_size", "loyalty_tier"),
                expirationDate = liveUntil
            )
            val withoutAny = newGrant(userId = null, sessionId = null, grantType = "client_credentials")

            val stored = grants.findById(id)!!
            val storedWithoutAny = grants.findById(withoutAny)!!

            assertEquals(authenticationDate, stored.authenticationDate)
            assertArrayEquals(arrayOf("loyalty_tier"), stored.requestedIdTokenClaims)
            assertArrayEquals(arrayOf("shoe_size", "loyalty_tier"), stored.requestedUserinfoClaims)
            assertEquals(liveUntil, stored.expirationDate)
            assertNull(storedWithoutAny.authenticationDate)
            assertArrayEquals(emptyArray<String>(), storedWithoutAny.requestedIdTokenClaims)
            assertArrayEquals(emptyArray<String>(), storedWithoutAny.requestedUserinfoClaims)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Refuses a second grant for one session, as the key being taken`(database: Database) =
        withFixture(database) {
            val sessionId = UUID.randomUUID()
            newGrant(newUser(), sessionId = sessionId)

            val refusal = assertThrows<Exception> { newGrant(newUser(), sessionId = sessionId) }

            assertTrue(refusal.isRowAlreadyThere(), "The refusal does not read as the key being taken.")
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Admits any number of grants no session opened`(database: Database) = withFixture(database) {
        val grants = repository<GrantRepository>()
        val first = newGrant(userId = null, sessionId = null, grantType = "client_credentials")
        val second = newGrant(userId = null, sessionId = null, grantType = "client_credentials")

        assertNotNull(grants.findById(first))
        assertNotNull(grants.findById(second))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAt - Revokes the grant it names`(database: Database) = withFixture(database) {
        val grants = repository<GrantRepository>()
        val userId = newUser()
        val id = saveLiveGrant(userId)
        val untouched = saveLiveGrant(userId)

        assertEquals(1, grants.updateRevokedAt(id, revokedAt, "ADMIN", userId))

        val stored = grants.findById(id)!!
        assertEquals(revokedAt, stored.revokedAt)
        assertEquals("ADMIN", stored.revokedBy)
        assertEquals(userId, stored.revokedById)
        assertNull(grants.findById(untouched)!!.revokedAt)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAt - Takes a grant holding a token without an expiry as live`(database: Database) =
        withFixture(database) {
            val grants = repository<GrantRepository>()
            val id = newGrant(newUser(), clientId = clientId, audienceId = audienceId)
            newToken(id, expirationDate = null, type = "REFRESH")

            assertEquals(1, grants.updateRevokedAt(id, revokedAt, "CLIENT", null))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAt - Takes a grant whose code is still to be exchanged as live`(database: Database) =
        withFixture(database) {
            val grants = repository<GrantRepository>()
            val id = newGrant(newUser(), clientId = clientId, audienceId = audienceId)
            newCode(id, "grant-repository-test-pending-code", expirationDate = liveUntil)

            assertEquals(1, grants.updateRevokedAt(id, revokedAt, "ADMIN", null))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAt - Leaves a grant whose code is exchanged or expired, with no token, as it was`(
        database: Database
    ) = withFixture(database) {
        val grants = repository<GrantRepository>()
        val exchanged = newGrant(newUser(), clientId = clientId, audienceId = audienceId)
        newCode(
            exchanged, "grant-repository-test-exchanged-code",
            expirationDate = liveUntil, exchangeDate = BASE_DATE
        )
        val expired = newGrant(newUser(), clientId = clientId, audienceId = audienceId)
        newCode(expired, "grant-repository-test-expired-code", expirationDate = revokedAt.minusMinutes(1))

        assertEquals(0, grants.updateRevokedAt(exchanged, revokedAt, "ADMIN", null))
        assertEquals(0, grants.updateRevokedAt(expired, revokedAt, "ADMIN", null))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAt - Leaves a grant past its own lifetime as it was`(database: Database) =
        withFixture(database) {
            val grants = repository<GrantRepository>()
            val id = newGrant(
                newUser(),
                clientId = clientId,
                audienceId = audienceId,
                expirationDate = revokedAt.minusMinutes(1)
            )
            newToken(id, expirationDate = null, type = "REFRESH")

            assertEquals(0, grants.updateRevokedAt(id, revokedAt, "ADMIN", null))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAt - Leaves a grant already revoked, or lapsed, as it was`(database: Database) =
        withFixture(database) {
            val grants = repository<GrantRepository>()
            val userId = newUser()
            val revokedEarlier = saveRevokedEarlierGrant(userId)
            val lapsed = saveLapsedGrant(userId)
            val tokenless = newGrant(userId, clientId = clientId, audienceId = audienceId)

            assertEquals(0, grants.updateRevokedAt(revokedEarlier, revokedAt, "ADMIN", userId))
            assertEquals(0, grants.updateRevokedAt(lapsed, revokedAt, "ADMIN", userId))
            assertEquals(0, grants.updateRevokedAt(tokenless, revokedAt, "ADMIN", userId))

            grants.assertLeftAsTheyWere(revokedEarlier, lapsed)
            assertNull(grants.findById(tokenless)!!.revokedAt)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtByActorGrantId - Revokes the live grants exchanged from it and counts them`(
        database: Database
    ) = withFixture(database) {
        val grants = repository<GrantRepository>()
        val userId = newUser()
        val actorGrantId = saveLiveGrant(userId = null, grantType = "client_credentials", sessionId = null)
        val exchanged = saveLiveGrant(userId, actorGrantId = actorGrantId)
        val revokedEarlier = saveRevokedEarlierGrant(userId, actorGrantId = actorGrantId)
        val lapsed = saveLapsedGrant(userId, actorGrantId = actorGrantId)
        val unrelated = saveLiveGrant(userId)

        val count = grants.updateRevokedAtByActorGrantId(actorGrantId, revokedAt, "CLIENT", null)

        assertEquals(1, count)
        assertEquals(revokedAt, grants.findById(exchanged)?.revokedAt)
        assertNull(grants.findById(actorGrantId)!!.revokedAt)
        assertNull(grants.findById(unrelated)!!.revokedAt)
        grants.assertLeftAsTheyWere(revokedEarlier, lapsed)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtByUserIdAndClientId - Revokes the live grants on both keys at once`(database: Database) =
        withFixture(database) {
            val grants = repository<GrantRepository>()
            val userId = newUser()
            val otherUserId = newUser()
            val revoked = saveLiveGrant(userId)
            val revokedEarlier = saveRevokedEarlierGrant(userId)
            val lapsed = saveLapsedGrant(userId)
            val otherClient = saveLiveGrant(userId, clientId = otherClientId)
            val otherUser = saveLiveGrant(otherUserId)

            val count = grants.updateRevokedAtByUserIdAndClientId(userId, clientId, revokedAt, "ADMIN", userId)

            assertEquals(1, count)
            assertEquals(revokedAt, grants.findById(revoked)?.revokedAt)
            assertNull(grants.findById(otherClient)!!.revokedAt)
            assertNull(grants.findById(otherUser)!!.revokedAt)
            grants.assertLeftAsTheyWere(revokedEarlier, lapsed)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtByUserIdAndAudienceIdAndActorGrantIdIsNull - Revokes the live grants of the audience`(
        database: Database
    ) = withFixture(database) {
        val grants = repository<GrantRepository>()
        val userId = newUser()
        val otherUserId = newUser()
        val revoked = saveLiveGrant(userId)
        val otherClient = saveLiveGrant(userId, clientId = otherClientId)
        val revokedEarlier = saveRevokedEarlierGrant(userId)
        val lapsed = saveLapsedGrant(userId)
        val otherAudience = saveLiveGrant(userId, audienceId = otherAudienceId)
        val otherUser = saveLiveGrant(otherUserId)

        val count = grants.updateRevokedAtByUserIdAndAudienceIdAndActorGrantIdIsNull(
            userId, audienceId, revokedAt, "CONSENT_REVOKED", userId
        )

        assertEquals(2, count)
        assertEquals(revokedAt, grants.findById(revoked)?.revokedAt)
        assertEquals(revokedAt, grants.findById(otherClient)?.revokedAt)
        assertNull(grants.findById(otherAudience)!!.revokedAt)
        assertNull(grants.findById(otherUser)!!.revokedAt)
        grants.assertLeftAsTheyWere(revokedEarlier, lapsed)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtByUserIdAndAudienceIdAndActorGrantIdIsNull - Leaves an act-as grant in service`(
        database: Database
    ) = withFixture(database) {
        val grants = repository<GrantRepository>()
        val userId = newUser()
        val actAs = saveLiveGrant(userId, sessionId = null, actorGrantId = UUID.randomUUID())

        val count = grants.updateRevokedAtByUserIdAndAudienceIdAndActorGrantIdIsNull(
            userId, audienceId, revokedAt, "CONSENT_REVOKED", userId
        )

        assertEquals(0, count)
        assertNull(grants.findById(actAs)!!.revokedAt)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateRevokedAtByUserId - Revokes every live grant of the user and counts them`(database: Database) =
        withFixture(database) {
            val grants = repository<GrantRepository>()
            val userId = newUser()
            val otherUserId = newUser()
            val first = saveLiveGrant(userId)
            val second = saveLiveGrant(userId, clientId = otherClientId)
            val revokedEarlier = saveRevokedEarlierGrant(userId)
            val lapsed = saveLapsedGrant(userId)
            val other = saveLiveGrant(otherUserId)

            val count = grants.updateRevokedAtByUserId(userId, revokedAt, "ADMIN", userId)

            assertEquals(2, count)
            assertEquals(revokedAt, grants.findById(first)?.revokedAt)
            assertEquals(revokedAt, grants.findById(second)?.revokedAt)
            assertNull(grants.findById(other)!!.revokedAt)
            grants.assertLeftAsTheyWere(revokedEarlier, lapsed)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteFinished - Deletes the grants no token is left under, unless a code names them`(database: Database) =
        withFixture(database) {
            val grants = repository<GrantRepository>()
            val userId = newUser()
            val tokenless = newGrant(userId, clientId = clientId, audienceId = audienceId)
            val revokedTokenless = newGrant(userId, revokedAt = earlierRevokedAt, revokedBy = "CLIENT")
            val withToken = saveLiveGrant(userId)
            val namedByCode = newGrant(
                userId,
                revokedAt = earlierRevokedAt,
                revokedBy = "AUTHORIZATION_CODE_REPLAYED"
            )
            newCode(namedByCode, "grant-repository-test-code")

            val count = grants.deleteFinished()

            assertEquals(2, count)
            assertNull(grants.findById(tokenless))
            assertNull(grants.findById(revokedTokenless))
            assertNotNull(grants.findById(withToken))
            assertNotNull(grants.findById(namedByCode))
        }

    /** Asserts that [revokedEarlier] still carries the revocation it was saved with, and [lapsed] none. */
    private suspend fun GrantRepository.assertLeftAsTheyWere(revokedEarlier: UUID, lapsed: UUID) {
        val storedRevokedEarlier = findById(revokedEarlier)!!
        assertEquals(earlierRevokedAt, storedRevokedEarlier.revokedAt)
        assertEquals("CLIENT", storedRevokedEarlier.revokedBy)
        assertNull(storedRevokedEarlier.revokedById)
        assertNull(findById(lapsed)!!.revokedAt)
    }

    /** Saves a grant holding a token that has not expired at [revokedAt]. */
    private suspend fun RepositoryFixture.saveLiveGrant(
        userId: UUID?,
        clientId: String = this@GrantRepositoryTest.clientId,
        audienceId: String = this@GrantRepositoryTest.audienceId,
        grantType: String = "authorization_code",
        sessionId: UUID? = UUID.randomUUID(),
        actorGrantId: UUID? = null
    ): UUID = newGrant(
        userId,
        clientId = clientId,
        audienceId = audienceId,
        grantType = grantType,
        sessionId = sessionId,
        actorGrantId = actorGrantId
    ).also { newToken(it, expirationDate = liveUntil) }

    /** Saves a grant revoked before [revokedAt], whose token would otherwise still be live. */
    private suspend fun RepositoryFixture.saveRevokedEarlierGrant(
        userId: UUID,
        actorGrantId: UUID? = null
    ): UUID = newGrant(
        userId,
        clientId = clientId,
        audienceId = audienceId,
        actorGrantId = actorGrantId,
        revokedAt = earlierRevokedAt,
        revokedBy = "CLIENT"
    ).also { newToken(it, expirationDate = liveUntil) }

    /** Saves a grant whose every token expired before [revokedAt]. */
    private suspend fun RepositoryFixture.saveLapsedGrant(
        userId: UUID,
        actorGrantId: UUID? = null
    ): UUID = newGrant(
        userId,
        clientId = clientId,
        audienceId = audienceId,
        actorGrantId = actorGrantId
    ).also {
        newToken(it, expirationDate = revokedAt.minusMinutes(1))
        newToken(it, expirationDate = revokedAt.minusHours(1), type = "REFRESH")
    }
}

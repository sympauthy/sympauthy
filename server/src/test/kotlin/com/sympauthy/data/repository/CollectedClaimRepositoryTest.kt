package com.sympauthy.data.repository

import com.sympauthy.business.mapper.ClaimValueMapper
import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime
import java.util.*

/**
 * The claims collected against a user: the queries [CollectedClaimRepository] writes as raw SQL, and the
 * criteria query its file declares as an extension, none of which a unit test can reach.
 *
 * What they have to prove compiles either way — that a row is found by the key its own write computed
 * whatever spelling it publishes, that a scalar projection over `MAX` comes back typed, and that setting
 * a claim verified twice keeps the first date.
 */
class CollectedClaimRepositoryTest {

    /** Every claim value this class writes and queries by carries the test class's own name. */
    private val qualifier = "collected-claim-repository-test"
    private val aliceEmail = "alice@$qualifier.test"
    private val bobEmail = "bob@$qualifier.test"
    private val aliceName = "Alice-$qualifier"
    private val bobName = "Bob-$qualifier"
    private val charlieName = "Charlie-$qualifier"

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips a claim, and an absent value as null`(database: Database) = withFixture(database) {
        val claims = repository<CollectedClaimRepository>()
        val userId = newUser()
        val collected = saveClaim(userId, "email", aliceEmail)
        val absent = saveClaim(userId, "phone_number", value = null)

        val storedCollected = claims.findById(collected)
        val storedAbsent = claims.findById(absent)

        assertNotNull(storedCollected)
        assertEquals(encoded(aliceEmail), storedCollected!!.value)
        assertEquals(true, storedCollected.verified)
        assertEquals(BASE_DATE, storedCollected.collectionDate)
        assertEquals(BASE_DATE, storedCollected.verificationDate)
        assertNotNull(storedAbsent)
        assertNull(storedAbsent!!.value)
        assertNull(storedAbsent.verified)
        assertNull(storedAbsent.verificationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserId - Returns every claim of the user`(database: Database) = withFixture(database) {
        val users = seedUsers()

        val found = repository<CollectedClaimRepository>().findByUserId(users.aliceId)

        assertEquals(setOf("email", "name"), found.map { it.claim }.toSet())
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdAndClaimInList - Narrows to the named claims`(database: Database) = withFixture(database) {
        val users = seedUsers()

        val found = repository<CollectedClaimRepository>()
            .findByUserIdAndClaimInList(users.aliceId, listOf("email"))

        assertEquals(listOf("email"), found.map { it.claim })
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdInList - Returns the claims of every user in the list`(database: Database) =
        withFixture(database) {
            val users = seedUsers()

            val found = repository<CollectedClaimRepository>()
                .findByUserIdInList(listOf(users.aliceId, users.bobId))

            assertEquals(setOf(users.aliceId, users.bobId), found.map { it.userId }.toSet())
            assertEquals(4, found.size)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findByUserIdInListAndClaimInList - Narrows on both lists at once`(database: Database) =
        withFixture(database) {
            val users = seedUsers()

            val found = repository<CollectedClaimRepository>()
                .findByUserIdInListAndClaimInList(listOf(users.aliceId, users.bobId), listOf("name"))

            assertEquals(setOf(users.aliceId, users.bobId), found.map { it.userId }.toSet())
            assertEquals(listOf("name", "name"), found.map { it.claim })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findMaxCollectionDateByUserId - Returns the latest collection date`(database: Database) =
        withFixture(database) {
            val claims = repository<CollectedClaimRepository>()
            val userId = newUser()
            saveClaim(userId, "email", aliceEmail, collectedAt = BASE_DATE)
            saveClaim(userId, "name", aliceName, collectedAt = BASE_DATE.plusDays(2))

            val latest = claims.findMaxCollectionDateByUserId(userId)

            assertEquals(BASE_DATE.plusDays(2), latest)
        }

    /**
     * The aggregate answers for a user with no claim at all, where `MAX` over no row is a null the
     * projection has to carry back rather than an empty result the mapper would reject.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findMaxCollectionDateByUserId - Returns null when the user has no claim`(database: Database) =
        withFixture(database) {
            val userId = newUser()

            val latest = repository<CollectedClaimRepository>().findMaxCollectionDateByUserId(userId)

            assertNull(latest)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateClaimsToVerified - Verifies the claim and dates it`(database: Database) = withFixture(database) {
        val claims = repository<CollectedClaimRepository>()
        val userId = newUser()
        val id = saveClaim(userId, "email", aliceEmail, verified = null)
        val verifiedAt = BASE_DATE.plusDays(1)

        claims.updateClaimsToVerified(userId, "email", verifiedAt)

        val stored = claims.findById(id)
        assertEquals(true, stored?.verified)
        assertEquals(verifiedAt, stored?.verificationDate)
    }

    /**
     * The `CASE WHEN verified IS TRUE` is the whole point of the statement: a second verification must
     * leave the date the first one wrote.
     */
    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateClaimsToVerified - Keeps the date of an already verified claim`(database: Database) =
        withFixture(database) {
            val claims = repository<CollectedClaimRepository>()
            val userId = newUser()
            val id = saveClaim(userId, "email", aliceEmail, verified = null)
            val firstVerification = BASE_DATE.plusDays(1)

            claims.updateClaimsToVerified(userId, "email", firstVerification)
            claims.updateClaimsToVerified(userId, "email", BASE_DATE.plusDays(5))

            val stored = claims.findById(id)
            assertEquals(true, stored?.verified)
            assertEquals(firstVerification, stored?.verificationDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `updateClaimsToVerified - Leaves another claim of the user alone`(database: Database) =
        withFixture(database) {
            val claims = repository<CollectedClaimRepository>()
            val userId = newUser()
            saveClaim(userId, "email", aliceEmail, verified = null)
            val untouched = saveClaim(userId, "name", aliceName, verified = null)

            claims.updateClaimsToVerified(userId, "email", BASE_DATE.plusDays(1))

            val stored = claims.findById(untouched)!!
            assertNull(stored.verified)
            assertNull(stored.verificationDate)
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findCommittedClaimsMatching - Finds the claim of a pair holding that pair's key`(database: Database) =
        withFixture(database) {
            val users = seedUsers()
            val claims = repository<CollectedClaimRepository>()

            val found = claims.findCommittedClaimsMatching(
                listOf("email" to keyOf(bobEmail), "phone_number" to keyOf(bobEmail))
            )

            assertEquals(listOf(users.bobId), found.map { it.userId })
            assertTrue(claims.findCommittedClaimsMatching(listOf("name" to keyOf(bobEmail))).isEmpty())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findCommittedClaimsMatching - Finds a row written in one spelling by the key of another`(
        database: Database
    ) = withFixture(database) {
        // The whole point of the column: the row publishes the capitalisation its owner chose, and the
        // key it is found by folds it away.
        val userId = newUser()
        val typed = "Ada.Lovelace-$qualifier"
        saveClaim(userId, "name", typed)
        val claims = repository<CollectedClaimRepository>()

        val found = claims.findCommittedClaimsMatching(listOf("name" to keyOf(typed.uppercase())))

        assertEquals(listOf(userId), found.map { it.userId })
        assertEquals(encoded(typed), found.single().value)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findCommittedClaimsMatching - Matches a key only under the claim it was offered for`(
        database: Database
    ) = withFixture(database) {
        val users = seedUsers()
        val claims = repository<CollectedClaimRepository>()

        assertTrue(claims.findCommittedClaimsMatching(listOf("email" to keyOf(bobName))).isEmpty())
        assertEquals(
            listOf(users.bobId),
            claims.findCommittedClaimsMatching(listOf("name" to keyOf(bobName))).map { it.userId }
        )
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findCommittedClaimsMatching - Returns every row any of the pairs names`(database: Database) =
        withFixture(database) {
            val users = seedUsers()
            val claims = repository<CollectedClaimRepository>()

            val found = claims.findCommittedClaimsMatching(
                listOf("email" to keyOf(aliceEmail), "name" to keyOf(bobName))
            )

            assertEquals(setOf(users.aliceId, users.bobId, users.charlieId), found.map { it.userId }.toSet())
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findCommittedClaimsMatching - Returns nothing when no pair is offered`(database: Database) =
        withFixture(database) {
            seedUsers()

            assertTrue(repository<CollectedClaimRepository>().findCommittedClaimsMatching(emptyList()).isEmpty())
        }

    /**
     * Three users, of which the first and the third share an email and differ by name, so that a query
     * over both claims separates them and a query over the email alone does not.
     */
    private suspend fun RepositoryFixture.seedUsers(): SeededUsers {
        val aliceId = newUser()
        val bobId = newUser()
        val charlieId = newUser()
        saveClaim(aliceId, "email", aliceEmail)
        saveClaim(aliceId, "name", aliceName)
        saveClaim(bobId, "email", bobEmail)
        saveClaim(bobId, "name", bobName)
        saveClaim(charlieId, "email", aliceEmail)
        saveClaim(charlieId, "name", charlieName)
        return SeededUsers(aliceId, bobId, charlieId)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `findCommittedClaimsMatching - Hides a claim a session is still signing up`(database: Database) =
        withFixture(database) {
            val claims = repository<CollectedClaimRepository>()
            val session = newSession()
            val userId = newUser(sessionId = session.id)
            val email = "provisional@$qualifier.test"
            saveClaim(userId, "email", email, sessionId = session.id)
            val pair = listOf("email" to keyOf(email))

            assertTrue(claims.findCommittedClaimsMatching(pair).isEmpty())

            assertEquals(1, claims.clearSessionId(userId, session.id!!))

            assertEquals(listOf(userId), claims.findCommittedClaimsMatching(pair).map { it.userId })
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `clearSessionId - Promotes the claims of that session and no other`(database: Database) =
        withFixture(database) {
            val claims = repository<CollectedClaimRepository>()
            val session = newSession()
            val otherSession = newSession()
            val userId = newUser(sessionId = session.id)
            val otherUserId = newUser(sessionId = otherSession.id)
            val promoted = saveClaim(userId, "email", "one@$qualifier.test", sessionId = session.id)
            val untouched = saveClaim(otherUserId, "email", "two@$qualifier.test", sessionId = otherSession.id)

            assertEquals(1, claims.clearSessionId(userId, session.id!!))

            assertNull(claims.findById(promoted)?.sessionId)
            assertEquals(otherSession.id, claims.findById(untouched)?.sessionId)
        }

    private class SeededUsers(val aliceId: UUID, val bobId: UUID, val charlieId: UUID)

    private suspend fun RepositoryFixture.saveClaim(
        userId: UUID,
        claim: String,
        value: String?,
        collectedAt: LocalDateTime = BASE_DATE,
        verified: Boolean? = if (claim == "email") true else null,
        sessionId: UUID? = null
    ): UUID {
        val claims = repository<CollectedClaimRepository>()
        return claims.save(
            CollectedClaimEntity(
                userId = userId,
                claim = claim,
                value = value?.let { encoded(it) },
                foldedEqualityHash = repository<ClaimValueMapper>().toFoldedEqualityHash(value),
                verified = verified,
                collectionDate = collectedAt,
                verificationDate = if (verified == true) collectedAt else null,
                sessionId = sessionId
            )
        ).id!!.also { id -> deleteOnEnd { claims.deleteById(id) } }
    }

    /** A claim is stored as the JSON its mapper writes, so a read of the row has to expect the same. */
    private fun RepositoryFixture.encoded(value: Any?): String? =
        repository<ClaimValueMapper>().toEntity(value)

    /** The key an identifier row is found by, as the mapper that wrote the row computed it. */
    private fun RepositoryFixture.keyOf(value: String): Long =
        repository<ClaimValueMapper>().toFoldedEqualityHash(value)!!
}

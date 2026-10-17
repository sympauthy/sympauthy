package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * A token row and the grant it names, the count of the live ones under a grant, and the two deletes that
 * collect tokens: the expired, and those of a revoked grant.
 */
class AuthenticationTokenRepositoryTest {

    private val now = BASE_DATE.plusHours(1)

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `save - Round-trips the token and the grant it names`(database: Database) = withFixture(database) {
        val tokens = repository<AuthenticationTokenRepository>()
        val grantId = newGrant(newUser())
        val id = newToken(grantId, expirationDate = now, type = "REFRESH", grantType = "refresh_token")

        val stored = tokens.findById(id)

        assertNotNull(stored)
        assertEquals(grantId, stored!!.grantId)
        assertEquals("REFRESH", stored.type)
        assertEquals("refresh_token", stored.grantType)
        assertNull(stored.dpopJkt)
        assertEquals(BASE_DATE, stored.issueDate)
        assertEquals(now, stored.expirationDate)
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `countLiveByGrantId - Counts the tokens of the grant not expired yet, one without an expiry among them`(
        database: Database
    ) = withFixture(database) {
        val tokens = repository<AuthenticationTokenRepository>()
        val grantId = newGrant(newUser())
        val otherGrantId = newGrant(newUser())
        newToken(grantId, expirationDate = now.plusMinutes(1))
        newToken(grantId, expirationDate = now)
        newToken(grantId, expirationDate = null, type = "REFRESH")
        newToken(grantId, expirationDate = now.minusMinutes(1))
        newToken(otherGrantId, expirationDate = now.plusMinutes(1))

        assertEquals(3L, tokens.countLiveByGrantId(grantId, now))
    }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteByExpirationDateLessThan - Deletes the expired tokens and counts them`(database: Database) =
        withFixture(database) {
            val tokens = repository<AuthenticationTokenRepository>()
            val grantId = newGrant(newUser())
            val expired = newToken(grantId, expirationDate = now.minusMinutes(1))
            val expiringNow = newToken(grantId, expirationDate = now)
            val withoutExpiry = newToken(grantId, expirationDate = null, type = "REFRESH")

            assertEquals(1, tokens.deleteByExpirationDateLessThan(now))

            assertNull(tokens.findById(expired))
            assertNotNull(tokens.findById(expiringNow))
            assertNotNull(tokens.findById(withoutExpiry))
        }

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `deleteUnderRevokedGrants - Deletes every token of a revoked grant and counts them`(database: Database) =
        withFixture(database) {
            val tokens = repository<AuthenticationTokenRepository>()
            val userId = newUser()
            val revokedGrantId = newGrant(userId, revokedAt = BASE_DATE, revokedBy = "CLIENT")
            val liveGrantId = newGrant(userId)
            val access = newToken(revokedGrantId, expirationDate = now)
            val refresh = newToken(revokedGrantId, expirationDate = null, type = "REFRESH")
            val live = newToken(liveGrantId, expirationDate = now)

            assertEquals(2, tokens.deleteUnderRevokedGrants())

            assertNull(tokens.findById(access))
            assertNull(tokens.findById(refresh))
            assertNotNull(tokens.findById(live))
        }
}

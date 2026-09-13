package com.sympauthy.business.manager.lock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

/**
 * The mapping from a key to its row, pinned.
 *
 * Every assertion here is a golden value rather than a property, because the property that matters is
 * that the answer never changes: two instances running different versions of this would take different
 * rows for one key and exclude nothing, and a rolling deploy is exactly when that happens.
 */
class LockKeyTest {

    @Test
    fun `stripe - Maps a user to the row it has always mapped to`() {
        assertEquals(11, LockKey.User(UUID.fromString("00000000-0000-0000-0000-000000000000")).stripe)
        assertEquals(63, LockKey.User(UUID.fromString("6f3d9a12-4c5e-4b3a-8f21-0d7e5b9c1a44")).stripe)
    }

    @Test
    fun `stripe - Maps an identifier value to the row it has always mapped to`() {
        assertEquals(13, LockKey.IdentifierValue("someone@example.com").stripe)
        assertEquals(56, LockKey.IdentifierValue("").stripe)
    }

    @Test
    fun `stripe - Answers a different row for a value spelled differently`() {
        assertEquals(25, LockKey.IdentifierValue("SOMEONE@example.com").stripe)
    }

    @Test
    fun `stripe - Maps a provider identity to the row it has always mapped to`() {
        assertEquals(11, LockKey.ProviderSubject("google", "123").stripe)
        assertEquals(62, LockKey.ProviderSubject("discord", "subject-1").stripe)
    }

    @Test
    fun `stripe - Answers a different row for the same characters split differently`() {
        assertEquals(45, LockKey.ProviderSubject("google", "1").stripe)
        assertEquals(60, LockKey.ProviderSubject("google1", "23").stripe)
    }

    @Test
    fun `stripe - Answers a row the table holds, for every key`() {
        val stripes = (0 until 4096).map { LockKey.IdentifierValue("user$it@example.com").stripe }

        assertTrue(stripes.all { it in 0 until LockKey.STRIPE_COUNT }, "A key mapped outside the table: $stripes")
        assertEquals(LockKey.STRIPE_COUNT, stripes.distinct().size, "The keys did not reach every row.")
    }
}

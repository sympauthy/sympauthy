package com.sympauthy.business.model.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What makes two sightings one place — and, as much as a unit test can, what this must never do to get
 * there.
 *
 * The canonicalisation is the whole of the deduplication: keyed on the raw text, one caller through one
 * proxy presents two spellings and the record silently becomes one row per sign-in. So every case here
 * is a pair that must collapse, or a pair that must not.
 */
class SecurityContextKeyTest {

    @Test
    fun `securityContextKey - One address written two ways keys the same`() {
        val expanded = observedRequestOf(ipAddress = "2001:0db8:0000:0000:0000:0000:0000:0001")
        val compressed = observedRequestOf(ipAddress = "2001:db8::1")

        assertEquals(compressed.securityContextKey().fingerprint, expanded.securityContextKey().fingerprint)
    }

    @Test
    fun `securityContextKey - An IPv4-mapped address keys as the IPv4 it is`() {
        val mapped = observedRequestOf(ipAddress = "::ffff:203.0.113.7")
        val plain = observedRequestOf(ipAddress = "203.0.113.7")

        assertEquals(plain.securityContextKey().fingerprint, mapped.securityContextKey().fingerprint)
        assertEquals("203.0.113.7", mapped.securityContextKey().ip)
    }

    @Test
    fun `securityContextKey - Upper and lower case hex in IPv6 key the same`() {
        val upper = observedRequestOf(ipAddress = "2001:DB8::ABCD")
        val lower = observedRequestOf(ipAddress = "2001:db8::abcd")

        assertEquals(lower.securityContextKey().fingerprint, upper.securityContextKey().fingerprint)
    }

    @Test
    fun `securityContextKey - A bracketed address keys as the address inside it`() {
        assertEquals(
            observedRequestOf(ipAddress = "2001:db8::1").securityContextKey().fingerprint,
            observedRequestOf(ipAddress = "[2001:db8::1]").securityContextKey().fingerprint
        )
    }

    /** A scope id names an interface on the machine, not a different place. */
    @Test
    fun `securityContextKey - An address with a scope id keys as the address`() {
        assertEquals(
            observedRequestOf(ipAddress = "2001:db8::1").securityContextKey().fingerprint,
            observedRequestOf(ipAddress = "2001:db8::1%eth0").securityContextKey().fingerprint
        )
    }

    /**
     * A name is exactly what must not be resolved — the value may be the contents of a header a caller
     * wrote — so it is kept as it stands and keys as itself. A resolver would also have made this test
     * reach the network.
     */
    @Test
    fun `securityContextKey - A value that is not an address literal is kept as it stands`() {
        val key = observedRequestOf(ipAddress = " not.an.address.at.all ").securityContextKey()

        assertEquals("not.an.address.at.all", key.ip)
    }

    @Test
    fun `securityContextKey - Four numeric groups that are not an address are not treated as one`() {
        // 1.2.3.4.5 has five groups and 300.1.1.1 has an octet no address holds. Neither is canonical
        // and neither may be handed to anything that would look it up.
        assertEquals("1.2.3.4.5", observedRequestOf(ipAddress = "1.2.3.4.5").securityContextKey().ip)
        assertEquals("300.1.1.1", observedRequestOf(ipAddress = "300.1.1.1").securityContextKey().ip)
    }

    /**
     * Every one of these is nothing but hex digits, colons and dots, and none of them is an address.
     * `InetAddress.getByName` resolves the ones beginning with a dot — it parses a literal only when the
     * first character is a hex digit or a colon — so a guard on the character set alone would have handed
     * caller-written header text to the platform's name service, once per sign-in.
     */
    @Test
    fun `securityContextKey - A colon-bearing value that is not an address is kept as it stands`() {
        for (value in listOf(".:1", ".:.", "...:...", ":1", "12345::1", "1:2:3:4:5:6:7:8:9", "::1::2", "1:")) {
            assertEquals(value, observedRequestOf(ipAddress = value).securityContextKey().ip, value)
        }
    }

    @Test
    fun `securityContextKey - Canonicalises every shape an IPv6 literal is written in`() {
        val shapes = mapOf(
            "::" to "0:0:0:0:0:0:0:0",
            "::1" to "0:0:0:0:0:0:0:1",
            "1::" to "1:0:0:0:0:0:0:0",
            "2001:db8::8:800:200c:417a" to "2001:db8:0:0:8:800:200c:417a",
            "2001:0db8:0000:0000:0000:0000:0000:0001" to "2001:db8:0:0:0:0:0:1"
        )
        shapes.forEach { (written, canonical) ->
            assertEquals(canonical, observedRequestOf(ipAddress = written).securityContextKey().ip, written)
        }
    }

    @Test
    fun `securityContextKey - An address it could not canonicalise is bounded too`() {
        val long = "9".repeat(MAX_IP_LENGTH + 500) + ":"

        val key = observedRequestOf(ipAddress = long).securityContextKey()

        assertEquals(MAX_IP_LENGTH, key.ip.length)
        // What is stored is what was keyed, so varying the tail cannot mint a place the row disagrees with.
        assertEquals(observedRequestOf(ipAddress = key.ip).securityContextKey().fingerprint, key.fingerprint)
    }

    @Test
    fun `securityContextKey - Two addresses key differently`() {
        assertNotEquals(
            observedRequestOf(ipAddress = "203.0.113.7").securityContextKey().fingerprint,
            observedRequestOf(ipAddress = "203.0.113.8").securityContextKey().fingerprint
        )
    }

    @Test
    fun `securityContextKey - The user agent is part of the key`() {
        assertNotEquals(
            observedRequestOf(userAgent = "one").securityContextKey().fingerprint,
            observedRequestOf(userAgent = "two").securityContextKey().fingerprint
        )
    }

    /**
     * An absent user agent is a different sighting from an empty one, and saying so is what stops a
     * caller sending nothing from keying as one sending "".
     */
    @Test
    fun `securityContextKey - An absent user agent keys differently from an empty one`() {
        assertNotEquals(
            observedRequestOf(userAgent = null).securityContextKey().fingerprint,
            observedRequestOf(userAgent = "").securityContextKey().fingerprint
        )
    }

    /**
     * The separator is a byte no field value may carry, so no user agent can be written to look like the
     * end of the address.
     */
    @Test
    fun `securityContextKey - A user agent cannot be written to look like another address`() {
        assertNotEquals(
            observedRequestOf(ipAddress = "203.0.113.7", userAgent = "x").securityContextKey().fingerprint,
            observedRequestOf(ipAddress = "203.0.113.7x", userAgent = null).securityContextKey().fingerprint
        )
    }

    @Test
    fun `securityContextKey - The user agent is bounded, and bounded before it is keyed`() {
        val long = "a".repeat(MAX_USER_AGENT_LENGTH + 500)

        val key = observedRequestOf(userAgent = long).securityContextKey()

        assertEquals(MAX_USER_AGENT_LENGTH, key.userAgent!!.length)
        // What is stored is what was keyed, so the row's own fingerprint is recomputable from it.
        assertEquals(
            observedRequestOf(userAgent = key.userAgent).securityContextKey().fingerprint,
            key.fingerprint
        )
    }

    @Test
    fun `securityContextKey - Keeps no user agent where the caller sent none`() {
        assertNull(observedRequestOf(userAgent = null).securityContextKey().userAgent)
    }

    @Test
    fun `securityContextKey - Is a hex digest of a fixed width`() {
        val fingerprint = observedRequestOf().securityContextKey().fingerprint

        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in "0123456789abcdef" }, fingerprint)
    }
}

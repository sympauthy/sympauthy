package com.sympauthy.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rule against patterns this test writes out itself, so that what it holds is the rule and not the
 * server's current configuration classes. The patterns below are shaped like the real ones — a literal,
 * an id the operator chooses, a list, a map — and are otherwise this test's own.
 */
class DeclaredConfigurationKeysTest {

    private val keys = DeclaredConfigurationKeys(
        listOf(
            "advanced.keys-generation-strategy",
            "audiences.*.token-audience",
            "clients.*.allowed-scopes",
            "clients.*.default-scopes",
            "clients.*.uris.**",
            "clients.*.authorization-webhook.url",
            "rules.user[*].name",
            "rules.user[*].scopes",
            "templates.clients.*.authorization-flow",
            "ui.display-name"
        )
    )

    @Test
    fun `answersFor - A key under a prefix a pattern declares is ours`() {
        assertTrue(keys.answersFor("ui.display-name"))
        assertTrue(keys.answersFor("ui.mail.background-color"))
    }

    @Test
    fun `answersFor - A key under any other prefix is not`() {
        assertFalse(keys.answersFor("micronaut.server.port"))
        assertFalse(keys.answersFor("uis.display-name"))
    }

    @Test
    fun `findUnboundKeys - A key matching a pattern entire binds`() {
        assertEquals(emptyList<String>(), keys.findUnboundKeys("advanced.keys-generation-strategy", "auto-increment"))
    }

    @Test
    fun `findUnboundKeys - A key is matched hyphenated`() {
        assertEquals(emptyList<String>(), keys.findUnboundKeys("advanced.keysGenerationStrategy", "auto-increment"))
    }

    @Test
    fun `findUnboundKeys - An id the operator chose matches the star that stands for it`() {
        assertEquals(emptyList<String>(), keys.findUnboundKeys("clients.admin.allowed-scopes", listOf("openid")))
    }

    @Test
    fun `findUnboundKeys - A key that is a prefix of a pattern binds`() {
        assertEquals(emptyList<String>(), keys.findUnboundKeys("audiences.default", emptyMap<String, Any>()))
        assertEquals(emptyList<String>(), keys.findUnboundKeys("templates.clients", emptyMap<String, Any>()))
    }

    @Test
    fun `findUnboundKeys - A key under a map binds whatever it names`() {
        assertEquals(emptyList<String>(), keys.findUnboundKeys("clients.admin.uris.sign-in", "/sign-in"))
        assertEquals(emptyList<String>(), keys.findUnboundKeys("clients.admin.uris.sign-in.fragment", "#top"))
    }

    @Test
    fun `findUnboundKeys - A key no pattern declares is unbound`() {
        assertEquals(
            listOf("ui.mail.background-color"),
            keys.findUnboundKeys("ui.mail.background-color", "#ffffff")
        )
    }

    @Test
    fun `findUnboundKeys - A key the operator indexed is read as the entry it names`() {
        assertEquals(emptyList<String>(), keys.findUnboundKeys("rules.user[0].name", "admins"))
        assertEquals(emptyList<String>(), keys.findUnboundKeys("clients.admin.allowed-scopes[0]", "openid"))
    }

    @Test
    fun `findUnboundKeys - The entries of a list are read from the value the key holds`() {
        val rules = listOf(
            mapOf("name" to "admins", "scopes" to listOf("admin:users:read")),
            mapOf("nmae" to "readers", "scopes" to listOf("openid"))
        )

        assertEquals(listOf("rules.user[1].nmae"), keys.findUnboundKeys("rules.user", rules))
    }

    @Test
    fun `findUnboundKeys - A list of entries written as a map binds to nothing`() {
        assertEquals(listOf("rules.user.name"), keys.findUnboundKeys("rules.user.name", "admins"))
    }

    @Test
    fun `findUnboundKeys - An index written where no list is binds to nothing`() {
        assertEquals(
            listOf("clients.admin[0].allowed-scopes"),
            keys.findUnboundKeys("clients.admin[0].allowed-scopes", listOf("openid"))
        )
        assertEquals(
            listOf("audiences.default[0].token-audience"),
            keys.findUnboundKeys("audiences.default[0].token-audience", "default")
        )
    }

    @Test
    fun `findUnboundKeys - An index too long to be a number is still read as an index`() {
        assertEquals(emptyList<String>(), keys.findUnboundKeys("rules.user[99999999999].name", "admins"))
    }

    @Test
    fun `findUnboundKeys - An entry naming nothing under it is a key of its own`() {
        val rules = listOf(mapOf("nmae" to emptyMap<String, Any>()))

        assertEquals(listOf("rules.user[0].nmae"), keys.findUnboundKeys("rules.user", rules))
    }

    @Test
    fun `findUnboundKeys - A list of values holds no key of its own`() {
        assertEquals(
            emptyList<String>(),
            keys.findUnboundKeys("clients.admin.allowed-scopes", listOf("openid", "profile"))
        )
    }

    @Test
    fun `nearestKeyOrNull - A key sharing a word with a declared one is answered by it`() {
        assertEquals(
            "templates.clients.default.authorization-flow",
            keys.nearestKeyOrNull("templates.clients.default.flow")
        )
    }

    @Test
    fun `nearestKeyOrNull - A misspelt segment is corrected where it was written`() {
        assertEquals("audiences.default.token-audience", keys.nearestKeyOrNull("audiences.default.token-audence"))
        assertEquals("clients.admin.allowed-scopes", keys.nearestKeyOrNull("clients.admin.allowd-scopes"))
        assertEquals("rules.user[1].name", keys.nearestKeyOrNull("rules.user[1].nmae"))
    }

    @Test
    fun `nearestKeyOrNull - A corrected entry keeps the index it was written with`() {
        assertEquals("rules.user[0].name", keys.nearestKeyOrNull("rules.usr[0].name"))
    }

    @Test
    fun `nearestKeyOrNull - Two keys equally near are answered by the same one every time`() {
        assertEquals("clients.admin.allowed-scopes", keys.nearestKeyOrNull("clients.admin.scopes"))
    }

    @Test
    fun `nearestKeyOrNull - A key nothing resembles is answered by nothing`() {
        assertNull(keys.nearestKeyOrNull("ui.mail.background-color"))
    }

    @Test
    fun `nearestKeyOrNull - A key needing a second correction to bind is answered by nothing`() {
        assertNull(keys.nearestKeyOrNull("templates.clints.default.flow"))
    }
}

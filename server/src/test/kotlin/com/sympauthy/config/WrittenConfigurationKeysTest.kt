package com.sympauthy.config

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * No bean is instantiated for these: the environment is built with the one source the case names and
 * started, which is what reads a property source, and nothing is asked of a database.
 */
class WrittenConfigurationKeysTest {

    private fun keysUnder(prefix: String, source: PropertySource): Set<String> {
        val builder = ApplicationContext.builder().deduceEnvironment(false).propertySources(source)
        return builder.build().use { context ->
            context.environment.start()
            WrittenConfigurationKeys(context.environment).under(prefix)
        }
    }

    private fun yaml(contents: String) = PropertySource.of(
        "test", YamlPropertySourceLoader().read("test", contents.trimIndent().byteInputStream())
    )

    @Test
    fun `under - Return a key written with nothing under it`() {
        // The one a properties class cannot answer for: it binds to null, as a key nobody wrote does.
        val source = yaml(
            """
            claims:
              sub:
                enabled:
            """
        )

        assertEquals(setOf("claims.sub.enabled"), keysUnder("claims.", source))
    }

    @Test
    fun `under - Return a key as the file spells it`() {
        val source = yaml(
            """
            claims:
              updated-at:
                type: string
            """
        )

        assertEquals(setOf("claims.updated-at.type"), keysUnder("claims.", source))
    }

    @Test
    fun `under - Return one key for a list a file wrote entry by entry`() {
        val source = PropertySource.of(
            "test",
            mapOf(
                "claims.sub.acl.readable-with-client-scopes-unconditionally[0]" to "users:claims:read",
                "claims.sub.acl.readable-with-client-scopes-unconditionally[1]" to "users:claims:write"
            )
        )

        assertEquals(
            setOf("claims.sub.acl.readable-with-client-scopes-unconditionally"),
            keysUnder("claims.", source)
        )
    }

    @Test
    fun `under - Return one key for a list a file wrote whole`() {
        val source = yaml(
            """
            claims:
              sub:
                acl:
                  readable-with-client-scopes-unconditionally:
                    - users:claims:read
            """
        )

        assertEquals(
            setOf("claims.sub.acl.readable-with-client-scopes-unconditionally"),
            keysUnder("claims.", source)
        )
    }

    @Test
    fun `under - Leave out a key written under a prefix merely beginning with the same letters`() {
        val source = yaml(
            """
            claims:
              email:
                type: email
            templates:
              claims:
                default:
                  enabled: "true"
            """
        )

        assertEquals(setOf("claims.email.type"), keysUnder("claims.", source))
    }
}

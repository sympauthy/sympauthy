package com.sympauthy.config.properties

import com.sympauthy.config.DeclaredConfigurationKey
import com.sympauthy.config.DeclaredConfigurationKeyReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Holds the header overrides to the shape that makes them bind.
 *
 * They are the only map this server declares on an interface rather than on a class with mutable
 * fields, and a map declared as anything but the subtree it opens would leave every override silently
 * doing nothing: the server would start, report itself ready, and read the profile's header instead of
 * the one an operator named.
 */
class SecurityContextConfigurationPropertiesTest {

    @Test
    fun `Declare the header overrides as the subtree an operator writes them in`() {
        val reader = DeclaredConfigurationKeyReader()
        val definition = reader.serverBeanDefinitions()
            .first { SecurityContextConfigurationProperties::class.java.isAssignableFrom(it.beanType) }

        assertEquals(
            listOf(
                "advanced.security-context.headers.**",
                "advanced.security-context.known-retention",
                "advanced.security-context.provider",
                "advanced.security-context.unknown-retention"
            ),
            reader.keysOf(definition).map(DeclaredConfigurationKey::pattern).sorted()
        )
    }
}

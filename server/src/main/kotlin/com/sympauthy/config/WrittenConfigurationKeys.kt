package com.sympauthy.config

import io.micronaut.context.env.Environment
import io.micronaut.context.env.EnvironmentPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.SystemPropertiesPropertySource
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * The keys a deployment wrote, as its files spell them.
 *
 * A rule about a key rather than about a value reads them from here. What a properties class binds
 * cannot answer for one: a key written with no value under it binds to null, which is
 * indistinguishable from a key nobody wrote, and a key the operator hyphenated binds to the same
 * field as the one they spelt with an underscore.
 *
 * The environment's own two sources are left out. `System.env` and `System.properties` share a
 * namespace with the whole machine, and the surface this server describes is a YAML file.
 */
@Singleton
class WrittenConfigurationKeys(
    @Inject private val environment: Environment
) {
    val sources: List<PropertySource>
        get() = environment.propertySources
            .filterNot { it is SystemPropertiesPropertySource || it is EnvironmentPropertySource }

    /**
     * Every key written under [prefix], which includes its trailing separator, so that `claims.` does
     * not answer for a domain merely beginning with the same letters.
     *
     * The index an entry of a list was addressed by is dropped, so that one list a deployment wrote is
     * one key however their file spells it: a YAML file holds the entries in the value of one key, and
     * a properties file writes a key per entry.
     */
    fun under(prefix: String): Set<String> = sources
        .flatten()
        .filter { it.startsWith(prefix) }
        .mapTo(mutableSetOf()) { INDEX.replace(it, "") }

    private companion object {

        val INDEX = Regex("""\[\d+]$""")
    }
}

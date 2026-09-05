package com.sympauthy.config

import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import io.micronaut.context.env.Environment
import io.micronaut.context.env.EnvironmentPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.SystemPropertiesPropertySource
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * The keys a deployment wrote under one of the server's own prefixes that bind to nothing, as errors
 * naming the key and the file it was read from.
 *
 * This is the other configuration bean [ConfigReadiness] holds that is not a domain: there is no value
 * here to parse, validate and hand to a manager, only the file itself held against what the server
 * declares. Everything else in this layer is written as the quintet a domain is written as, and this
 * is one of the two departures from it.
 *
 * The environment's own two sources are left alone. `System.env` and `System.properties` share a
 * namespace with the whole machine, and the surface this server describes is a YAML file.
 */
@Singleton
class UnboundConfigurationKeys(
    @Inject private val environment: Environment,
    @Inject private val declaredConfigurationKeyReader: DeclaredConfigurationKeyReader
) {
    private val declaredKeys by lazy { DeclaredConfigurationKeys(declaredConfigurationKeyReader.read()) }

    val configurationErrors: List<ConfigurationException> by lazy {
        environment.propertySources
            .filterNot { it is SystemPropertiesPropertySource || it is EnvironmentPropertySource }
            .flatMap(::unboundKeysIn)
    }

    private fun unboundKeysIn(source: PropertySource): List<ConfigurationException> {
        val location = source.origin.location()
        return source.filter(declaredKeys::answersFor)
            .flatMap { key -> declaredKeys.findUnboundKeys(key, source[key]) }
            .map { key -> unboundKey(key, location) }
    }

    private fun unboundKey(key: String, location: String): ConfigurationException {
        val nearestKey = declaredKeys.nearestKeyOrNull(key)
        return if (nearestKey == null) {
            configExceptionOf(key, "config.unknown_key", "location" to location)
        } else {
            configExceptionOf(
                key, "config.unknown_key.did_you_mean",
                "nearestKey" to nearestKey, "location" to location
            )
        }
    }
}

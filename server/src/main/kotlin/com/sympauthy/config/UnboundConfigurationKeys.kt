package com.sympauthy.config

import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import io.micronaut.context.env.PropertySource
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
 */
@Singleton
class UnboundConfigurationKeys(
    @Inject private val writtenConfigurationKeys: WrittenConfigurationKeys,
    @Inject private val declaredConfigurationKeyReader: DeclaredConfigurationKeyReader
) {
    private val declaredKeys by lazy { DeclaredConfigurationKeys(declaredConfigurationKeyReader.read()) }

    val configurationErrors: List<ConfigurationException> by lazy {
        writtenConfigurationKeys.sources.flatMap(::unboundKeysIn)
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

package com.sympauthy.config

import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.util.toAbsoluteUri
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Singleton
import java.net.URI
import java.time.Duration

/**
 * The parsing of configuration from Micronaut has a few loophole for our usage:
 * - it does not emit error message for invalid value.
 * - it is lazy. Error will be discovered when the config will be used.
 *
 * However, as the configuration is at the center of SympAuthy, we want:
 * - to report configuration issues as soon as the server starts to allow the user to fix and restart as fast as possible.
 * - we want explicit error message to help the user to fix errors.
 *
 * Therefor we must :
 * - deprive Micronaut of the parsing, all the configuration properties will only handle String, Array and Map.
 */
@Singleton
class ConfigParser {

    fun <C : Any> getOrThrow(config: C, key: String, value: (C) -> String?): String {
        return value(config) ?: throw configExceptionOf(key, "config.missing")
    }

    fun <C : Any> getString(config: C, key: String, value: (C) -> String?): String? {
        val value = value(config) ?: return null
        if (value.isBlank()) {
            throw configExceptionOf(key, "config.empty")
        }
        return value
    }

    fun <C : Any> getStringOrThrow(config: C, key: String, value: (C) -> String?): String {
        val value = getOrThrow(config, key, value)
        if (value.isBlank()) {
            throw configExceptionOf(key, "config.empty")
        }
        return value
    }

    fun <C : Any> getBoolean(config: C, key: String, value: (C) -> String?): Boolean? {
        val value = value(config) ?: return null
        return when (value.uppercase()) {
            "TRUE", "T", "YES", "Y" -> true
            "FALSE", "F", "NO", "N" -> false
            else -> throw configExceptionOf(key, "config.invalid_boolean")
        }
    }

    fun <C : Any> getInt(config: C, key: String, value: (C) -> String?): Int? {
        val value = value(config) ?: return null
        return value.toIntOrNull() ?: throw configExceptionOf(key, "config.invalid_int")
    }

    fun <C : Any> getDuration(config: C, key: String, value: (C) -> String?): Duration? {
        val value = value(config) ?: return null
        return ConversionService.SHARED.convert(value, Duration::class.java).orElse(null) ?: throw configExceptionOf(
            key, "config.invalid_duration"
        )
    }

    fun <C : Any> getUri(config: C, key: String, value: (C) -> String?): URI? {
        val value = value(config) ?: return null
        return UriBuilder.of(value).build()
    }

    fun <C : Any> getAbsoluteUriOrThrow(config: C, key: String, value: (C) -> String?): URI {
        return getOrThrow(config, key, value).toAbsoluteUri()
            ?: throw configExceptionOf(key, "config.invalid_url")
    }

    inline fun <C : Any, reified T : Enum<T>> getEnum(
        config: C,
        key: String,
        defaultValue: T,
        value: (C) -> String?
    ): T {
        val serializedValue = value(config) ?: return defaultValue
        return convertToEnum(key, serializedValue)
    }

    inline fun <reified T : Enum<T>> convertToEnum(key: String, value: String): T {
        val valueMap = enumValues<T>()
            .map {
                val configName = it.name.lowercase().replace("_", "-")
                configName to it
            }
            .toMap()
        return valueMap[value] ?: throw configExceptionOf(
            key,
            "config.invalid_enum_value",
            "value" to value,
            "supportedValues" to valueMap.keys.joinToString(", ")
        )
    }
}

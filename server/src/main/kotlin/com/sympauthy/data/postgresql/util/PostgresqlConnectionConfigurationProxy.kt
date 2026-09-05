package com.sympauthy.data.postgresql.util

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.client.MultiHostConfiguration
import io.r2dbc.postgresql.client.SingleHostConfiguration
import org.reactivestreams.Publisher

/**
 * This proxy uses reflection to access property on the configuration since most of the method
 * are not public.
 */
class PostgresqlConnectionConfigurationProxy(
    private val configuration: PostgresqlConnectionConfiguration
) {

    val applicationName: String?
        get() = readField("applicationName")

    val database: String?
        get() = readField("database")

    val multiHostConfiguration: MultiHostConfiguration?
        get() = readField("multiHostConfiguration")

    val password: Publisher<CharSequence>?
        get() = readField("password")

    val singleHostConfiguration: SingleHostConfiguration?
        get() = readField("singleHostConfiguration")

    val username: Publisher<CharSequence>?
        get() = readField("username")

    @Suppress("UNCHECKED_CAST")
    private fun <T> readField(fieldName: String): T? {
        val klass = PostgresqlConnectionConfiguration::class.java
        val field = klass.getDeclaredField(fieldName)
        return if (field.trySetAccessible()) {
            field.get(configuration) as? T
        } else null
    }
}

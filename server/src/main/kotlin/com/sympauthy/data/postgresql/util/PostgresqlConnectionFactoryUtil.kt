package com.sympauthy.data.postgresql.util

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory

val PostgresqlConnectionFactory.configurationProxy: PostgresqlConnectionConfigurationProxy
    get() {
        val klass = PostgresqlConnectionFactory::class.java
        val field = klass.getDeclaredField("configuration")
        if (field.trySetAccessible()) {
            return (field.get(this) as PostgresqlConnectionConfiguration)
                .let(::PostgresqlConnectionConfigurationProxy)
        } else error("Unable to access configuration of PostgreSQL connection factory")
    }

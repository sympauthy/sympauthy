package com.sympauthy.data.postgresql.migration

import com.sympauthy.data.migration.DriverFlywayConfigurationCustomizer
import com.sympauthy.data.postgresql.DefaultDataSourceIsPostgreSQL
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Named

@Context
@Named("default")
@ReflectiveAccess
@Requires(condition = DefaultDataSourceIsPostgreSQL::class)
class PostgreSQLFlywayConfigurationCustomizer : DriverFlywayConfigurationCustomizer("postgresql")

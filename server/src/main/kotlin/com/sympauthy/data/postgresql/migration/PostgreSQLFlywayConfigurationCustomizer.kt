package com.sympauthy.data.postgresql.migration

import com.sympauthy.data.migration.DriverFlywayConfigurationCustomizer
import com.sympauthy.data.postgresql.DefaultDataSourceIsPostgreSQL
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import jakarta.inject.Named

@Context
@Named("default")
@Requires(condition = DefaultDataSourceIsPostgreSQL::class)
class PostgreSQLFlywayConfigurationCustomizer : DriverFlywayConfigurationCustomizer("postgresql")

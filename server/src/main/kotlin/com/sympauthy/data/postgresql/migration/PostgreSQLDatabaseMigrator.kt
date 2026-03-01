package com.sympauthy.data.postgresql.migration

import com.sympauthy.data.migration.AbstractFlywayDatabaseMigrator
import com.sympauthy.data.postgresql.DefaultDataSourceIsPostgreSQL
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.flyway.FlywayConfigurationProperties
import jakarta.inject.Inject
import javax.sql.DataSource

@Context
@Requires(condition = DefaultDataSourceIsPostgreSQL::class)
class PostgreSQLDatabaseMigrator(
    @Inject private val dataSource: DataSource,
    @Inject private val configuration: FlywayConfigurationProperties
) : AbstractFlywayDatabaseMigrator(
    driver = "postgresql",
    dataSource = dataSource,
    configuration = configuration
)

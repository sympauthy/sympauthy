package com.sympauthy.data.h2.migration

import com.sympauthy.data.h2.DefaultDataSourceIsH2
import com.sympauthy.data.migration.AbstractFlywayDatabaseMigrator
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.flyway.FlywayConfigurationProperties
import jakarta.inject.Inject
import javax.sql.DataSource

@Context
@Requires(condition = DefaultDataSourceIsH2::class)
class H2DatabaseMigration(
    @Inject private val dataSource: DataSource,
    @Inject private val configuration: FlywayConfigurationProperties
): AbstractFlywayDatabaseMigrator(
    driver = "h2",
    dataSource = dataSource,
    configuration = configuration
)

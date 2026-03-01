package com.sympauthy.data.migration

import com.sympauthy.util.loggerForClass
import io.micronaut.flyway.FlywayConfigurationProperties
import jakarta.annotation.PostConstruct
import org.flywaydb.core.api.Location
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.io.path.pathString

/**
 * Applies Flyway database migrations for a specific SQL [driver] (e.g. `postgresql`, `h2`).
 *
 * ## Migration script layout
 *
 * Scripts must live under `resources/databases/<driver>/` so that a single classpath can ship
 * migrations for every supported database and each concrete migrator only picks up the scripts
 * that match its own driver:
 *
 * ```
 * resources/
 *   databases/
 *     postgresql/   ← picked up by PostgreSQLDatabaseMigrator (driver = "postgresql")
 *       V1__init.sql
 *     h2/           ← picked up by H2DatabaseMigration (driver = "h2")
 *       V1__init.sql
 * ```
 *
 * ## GraalVM native image
 *
 * Classpath resources are not automatically discoverable inside a native image binary.
 * Every `databases/<driver>` location must therefore be declared explicitly in two places:
 * - `resources/application.yml` under `flyway.datasources.default.locations`
 * - `build.gradle.kts` as a `-Dflyway.datasources.default.locations` build argument for the
 *   `nativeCompile` task, so Flyway can resolve them at build time.
 *
 * ## Startup ordering
 *
 * Concrete subclasses must be scoped with `@Context` (eager singleton). This guarantees that
 * the [DataSource] dependency is initialised and migrations are fully applied before any other
 * bean can interact with the database.
 */
abstract class AbstractFlywayDatabaseMigrator(
    private val driver: String,
    private val dataSource: DataSource,
    private val configuration: FlywayConfigurationProperties
) {

    private val logger = loggerForClass()

    /**
     * Initialize the migration of the database in the post construct to ensure migration
     * are finished before any other code can use it.
     */
    @PostConstruct
    fun init() {
        migrate()
    }

    /**
     * Run the database migrations.
     */
    fun migrate() {
        if (!configuration.isEnabled) {
            return
        }

        val locations = getClassPathLocationsForDriver()
        if (locations.isEmpty()) {
            logger.error("No migration found for $driver.")
        }

        val fluentConfiguration = configuration.fluentConfiguration.also {
            it.dataSource(dataSource)
            it.cleanDisabled(!configuration.isCleanSchema)
            it.configuration(configuration.properties)
            it.locations(*locations.toTypedArray())
        }

        val flyway = fluentConfiguration.load()
        if (configuration.isCleanSchema) {
            logger.info("Cleaning schema for ${configuration.nameQualifier} database.")
            flyway.clean()
        }

        fluentConfiguration.locations.joinToString(", ") { it.descriptor }.let {
            logger.info("Running migrations for ${configuration.nameQualifier} database and found at following locations: $it")
        }
        flyway.migrate()
    }

    /**
     * Filter the configured Flyway locations to only return classpath locations that contain
     * migrations for this [driver].
     *
     * A location is considered to target this driver if its last path segment matches [driver]
     * (e.g. `classpath:databases/postgresql` matches driver `postgresql`).
     * Regex-based locations are ignored since their path cannot be inspected reliably.
     *
     * Returns an empty list when no matching location is found.
     */
    private fun getClassPathLocationsForDriver(): List<Location> {
        return configuration.fluentConfiguration.locations
            .filter { it.isClassPath }
            .filter { isMigrationForDriver(it) }
    }

    /**
     * Check if the [location] ends with a directory named like the [driver].
     * Return true, means the location contains migration designed for the driver.
     * False otherwise.
     */
    private fun isMigrationForDriver(location: Location): Boolean {
        if (location.pathRegex != null) {
            return false
        }
        val locationPath = Path.of(location.rootPath)
        return locationPath.lastOrNull()?.pathString == driver
    }
}

package com.sympauthy.data.migration

import com.sympauthy.util.loggerForClass
import io.micronaut.flyway.FlywayConfigurationCustomizer
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.ResourceProvider
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.resource.LoadableResource
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Customize the configuration of Flyway to only keep locations containing script designed for the [driver].
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
 * ## Implementation
 *
 * Each supported driver must have its own singleton extending this class. Ex. for PG:
 *
 * ```
 * @Singleton
 * @Requires(condition = DefaultDataSourceIsPostgreSQL::class)
 * class PostgreSQLFlywayConfigurationCustomizer: DriverFlywayConfigurationCustomizer("postgresql")
 * ```
 */
open class DriverFlywayConfigurationCustomizer(
    val driver: String,
) : FlywayConfigurationCustomizer {

    private val logger = loggerForClass()

    override fun getName() = "default"

    override fun customizeFluentConfiguration(fluentConfiguration: FluentConfiguration) {
        logger.info("Customizing Flyway configuration for $driver")
        val locations = getClassPathLocationsForDriver(fluentConfiguration)
        fluentConfiguration.locations(*locations.toTypedArray())

        val resourceProvider = getResourceProviderForDriver(fluentConfiguration)
        if (resourceProvider != null) {
            fluentConfiguration.resourceProvider(resourceProvider)
        }
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
    private fun getClassPathLocationsForDriver(fluentConfiguration: FluentConfiguration): List<Location> {
        return fluentConfiguration.locations
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

    private fun getResourceProviderForDriver(fluentConfiguration: FluentConfiguration): ResourceProvider? {
        val resourceProvider = fluentConfiguration.resourceProvider ?: return null
        return object : ResourceProvider {
            override fun getResource(name: String) = resourceProvider.getResource(name)

            override fun getResources(
                prefix: String?,
                suffixes: Array<out String?>?
            ): Collection<LoadableResource>? {
                return resourceProvider.getResources(prefix, suffixes)?.filter(this@DriverFlywayConfigurationCustomizer::isResourceForDriver)
            }
        }
    }

    /**
     * Check if the [resource] contains a directory named like the [driver].
     * Return true, means the resource is a migration designed for the driver.
     * False otherwise.
     */
    private fun isResourceForDriver(resource: LoadableResource): Boolean {
        val resourcePath = Path.of(resource.absolutePath)
        if (resourcePath.parent == null) {
            return false
        }
        return resourcePath.parent.fileName.pathString == driver
    }
}

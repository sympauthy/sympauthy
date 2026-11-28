package com.sympauthy.data.postgresql

import com.sympauthy.platform.monitoring.loggerForClass
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration.DEFAULT_PORT
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider.*
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.*
import io.r2dbc.spi.Option
import jakarta.inject.Inject
import jakarta.inject.Named
import org.postgresql.ds.PGSimpleDataSource
import java.time.Duration
import javax.sql.DataSource

/**
 * Create a JDBC [DataSource] using the same connection information of the R2DBC [ConnectionFactory].
 * Unfortunately a JDBC connection is required to migrate our database using Flyway.
 * Otherwise, the whole application relies exclusively on the R2DBC connection.
 *
 * Everything deciding which tables the migration reaches and how the connection is secured is carried over:
 * the schema and the startup options, the SSL mode and its certificates, and the connect timeout. The statement
 * and lock timeouts are not, since they bound the server's queries and a migration is not one of them.
 *
 * @see PostgresqlConnectionFactoryProvider https://github.com/pgjdbc/r2dbc-postgresql/blob/main/src/main/java/io/r2dbc/postgresql/PostgresqlConnectionFactoryProvider.java
 */
@Factory
@Requires(condition = DefaultDataSourceIsPostgreSQL::class)
class PostgreSQLDefaultDataSourceFactory(
    @Inject private val connectionFactoryOptions: ConnectionFactoryOptions
) {

    private val log = loggerForClass()

    @Context
    @Named("default")
    fun provideDataSource(): DataSource {
        log.debug { "Initializing PostgreSQL JDBC data source from R2DBC connection factory." }
        val options = connectionFactoryOptions
        check(options.text(PROTOCOL) != FAILOVER_PROTOCOL) {
            "Unable to migrate a PostgreSQL database reached through several hosts."
        }
        check(!options.hasOption(SOCKET)) {
            "Unable to migrate a PostgreSQL database reached through a Unix socket."
        }
        return PGSimpleDataSource().apply {
            serverNames = arrayOf(options.getRequiredValue(HOST).toString())
            portNumbers = intArrayOf(options.text(PORT)?.toInt() ?: DEFAULT_PORT)
            databaseName = options.text(DATABASE)
            user = options.text(USER)
            password = options.text(PASSWORD)
            applicationName = options.text(APPLICATION_NAME)

            getSslMode(options)?.let { sslMode = it }
            options.text(SSL_ROOT_CERT)?.let { sslRootCert = it }
            options.text(SSL_CERT)?.let { sslCert = it }
            options.text(SSL_KEY)?.let { sslKey = it }
            options.text(SSL_PASSWORD)?.let { sslPassword = it }

            (options.text(CURRENT_SCHEMA) ?: options.text(SCHEMA))?.let { currentSchema = it }
            getStartupOptions(options)?.let { this.options = it }
            getConnectTimeoutInSeconds(options)?.let { connectTimeout = it }
        }
    }

    /**
     * Return the SSL mode in the spelling PGJDBC reads, which is libpq's: `verify-full` rather than `VERIFY_FULL`.
     * `ssl=true` without a mode is how R2DBC spells `verify-full`.
     */
    private fun getSslMode(options: ConnectionFactoryOptions): String? {
        val sslMode = options.text(SSL_MODE) ?: options.text(SSL_MODE_ALIAS)
        return when {
            sslMode != null -> sslMode.lowercase().replace('_', '-')
            options.text(SSL)?.toBoolean() == true -> "verify-full"
            else -> null
        }
    }

    /**
     * Return the startup parameters R2DBC sends as one `-c key=value` per parameter, the spelling PGJDBC expects.
     * R2DBC accepts them as a map, or as a `key=value;key=value` string when they come from the URL.
     */
    private fun getStartupOptions(options: ConnectionFactoryOptions): String? {
        val parameters = when (val value = options.value(OPTIONS)) {
            null -> return null
            is Map<*, *> -> value.map { (key, value) -> "$key" to "$value" }
            else -> value.toString().split(";")
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { it.substringBefore("=") to it.substringAfter("=") }
        }
        return parameters.joinToString(" ") { (key, value) -> "-c $key=${value.replace(" ", "\\ ")}" }
    }

    /**
     * Return the connect timeout rounded up to the second, the only unit PGJDBC reads.
     */
    private fun getConnectTimeoutInSeconds(options: ConnectionFactoryOptions): Int? {
        val timeout = when (val value = options.value(CONNECT_TIMEOUT)) {
            null -> return null
            is Duration -> value
            else -> Duration.parse(value.toString())
        }
        return ((timeout.toMillis() + 999) / 1000).toInt()
    }

    private fun ConnectionFactoryOptions.value(option: Option<*>): Any? = getValue(option)

    private fun ConnectionFactoryOptions.text(option: Option<*>): String? = value(option)?.toString()
}

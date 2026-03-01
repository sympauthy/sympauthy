package com.sympauthy.data.h2

import com.sympauthy.util.loggerForClass
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactoryProvider.*
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.*
import jakarta.inject.Inject
import jakarta.inject.Named
import org.h2.jdbcx.JdbcDataSource
import javax.sql.DataSource


/**
 * Create a JDBC [DataSource] using the same connection information of the R2DBC [ConnectionFactory].
 * Unfortunately a JDBC connection is required to migrate our database using Flyway.
 * Otherwise, the whole application relies exclusively on the R2DBC connection.
 *
 * Exemple of r2dbc to jdbc connection string conversions:
 * - r2dbc:h2:mem://localhost/sympauthy -> jdbc:h2:mem:sympauthy
 * - r2dbc:h2:file://localhost/./sympauthy -> jdbc:h2:file:./sympauthy
 */
@Factory
@Requires(condition = DefaultDataSourceIsH2::class)
class H2DefaultDataSourceFactory(
    @Inject private val connectionFactoryOptions: ConnectionFactoryOptions
) {

    private val log = loggerForClass()

    @Context
    @Named("default")
    fun provideDataSource(): DataSource {
        log.debug("Initializing H2 JDBC data source from R2DBC connection factory.")
        val connectionString = createJDBCConnectionString(connectionFactoryOptions)
        return JdbcDataSource().apply {
            setURL(connectionString)
            user = connectionFactoryOptions.getValue(USER) as String?
            password = connectionFactoryOptions.getValue(PASSWORD) as String?
        }
    }

    /**
     * Create a JDBC connection string pointing to the same database as the one configured in R2DBC [options].
     *
     * @see H2ConnectionConfiguration https://github.com/r2dbc/r2dbc-h2/blob/main/src/main/java/io/r2dbc/h2/H2ConnectionConfiguration.java
     * @see H2ConnectionFactoryProvider https://github.com/r2dbc/r2dbc-h2/blob/main/src/main/java/io/r2dbc/h2/H2ConnectionFactoryProvider.java
     */
    private fun createJDBCConnectionString(options: ConnectionFactoryOptions): String {
        return buildString {
            append("jdbc:h2:")
            getUrl(options)?.let(this::append) ?: throw IllegalStateException(
                "Unable to initiate H2 connection using R2DBC options ({$options})."
            )
            // FIXME implements support for options
        }
    }

    private fun getUrl(options: ConnectionFactoryOptions): String? {
        val url = options.getValue(URL) as String?
        return if (url == null) {
            val protocol = options.getRequiredValue(PROTOCOL)
            val database = options.getRequiredValue(DATABASE)
            when (protocol) {
                PROTOCOL_MEM, PROTOCOL_FILE -> "$protocol:$database"
                else -> null
            }
        } else url
    }
}

package com.sympauthy.data.h2

import com.sympauthy.util.loggerForClass
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.r2dbc.h2.H2ConnectionConfiguration
import io.r2dbc.h2.H2ConnectionFactoryProvider.*
import io.r2dbc.h2.H2ConnectionOption
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions.*
import io.r2dbc.spi.Option
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
            getOptions(options).forEach { append(";$it") }
        }
    }

    /**
     * Collect H2-specific options from both the semicolon-delimited [OPTIONS] string
     * and individually declared [H2ConnectionOption] keys.
     */
    private fun getOptions(options: ConnectionFactoryOptions): List<String> {
        val result = mutableListOf<String>()

        val optionsString = options.getValue(OPTIONS) as String?
        if (optionsString != null) {
            optionsString.split(";")
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach(result::add)
        }

        for (connectionOption in H2ConnectionOption.entries) {
            val key = connectionOption.key
            val ucOption = Option.valueOf<String>(key)
            val lcOption = Option.valueOf<String>(key.lowercase())
            val value = when {
                options.hasOption(ucOption) -> options.getRequiredValue(ucOption) as String
                options.hasOption(lcOption) -> options.getRequiredValue(lcOption) as String
                else -> null
            }
            if (value != null) {
                result.add("$key=$value")
            }
        }

        return result
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

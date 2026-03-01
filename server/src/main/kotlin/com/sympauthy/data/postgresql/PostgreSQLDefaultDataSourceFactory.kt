package com.sympauthy.data.postgresql

import com.sympauthy.data.postgresql.util.PostgresqlConnectionConfigurationProxy
import com.sympauthy.data.postgresql.util.configurationProxy
import com.sympauthy.util.loggerForClass
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.client.MultiHostConfiguration
import io.r2dbc.postgresql.client.SingleHostConfiguration
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import jakarta.inject.Named
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.runBlocking
import org.postgresql.ds.PGSimpleDataSource
import org.postgresql.ds.common.BaseDataSource
import javax.sql.DataSource

/**
 * Create a JDBC [DataSource] using the same connection information of the R2DBC [ConnectionFactory].
 * Unfortunately a JDBC connection is required to migrate our database using Flyway.
 * Otherwise, the whole application relies exclusively on the R2DBC connection.
 */
@Factory
@Requires(condition = DefaultDataSourceIsPostgreSQL::class)
class PostgreSQLDefaultDataSourceFactory(
    @Inject private val connectionFactory: ConnectionFactory
) {

    private val log = loggerForClass()

    @Context
    @Named("default")
    fun provideDataSource(): DataSource? {
        return if (connectionFactory is PostgresqlConnectionFactory) {
            log.debug("Initializing PostgreSQL JDBC data source from R2DBC connection factory.")
            runBlocking {
                convertConnectionFactoryToDataSource(connectionFactory.configurationProxy)
            }
        } else null
    }

    private suspend fun convertConnectionFactoryToDataSource(
        config: PostgresqlConnectionConfigurationProxy
    ): PGSimpleDataSource {
        return PGSimpleDataSource().apply {
            applicationName = config.applicationName
            databaseName = config.database
            user = config.username?.awaitFirst()?.toString()
            password = config.password?.awaitFirst()?.toString()

            config.singleHostConfiguration?.let { configureSingleHost(this, it) }
            config.multiHostConfiguration?.let { configureMultiHost(this, it) }
        }
    }

    private fun configureSingleHost(
        dataSource: BaseDataSource,
        hostConfiguration: SingleHostConfiguration
    ) {
        if (!hostConfiguration.isUseSocket) {
            dataSource.apply {
                serverNames = arrayOf(hostConfiguration.host)
                portNumbers = intArrayOf(hostConfiguration.port)
            }
        } else {
            throw IllegalStateException("Unsupported")
        }
    }

    private fun configureMultiHost(
        dataSource: BaseDataSource,
        hostConfiguration: MultiHostConfiguration
    ) {
        throw IllegalStateException("Unsupported")
    }
}

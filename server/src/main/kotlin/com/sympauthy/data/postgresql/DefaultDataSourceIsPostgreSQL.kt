package com.sympauthy.data.postgresql

import io.micronaut.context.condition.Condition
import io.micronaut.context.condition.ConditionContext
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory

/**
 * Condition checking if the default datasource is a PostgreSQL database.
 */
class DefaultDataSourceIsPostgreSQL : Condition {

    override fun matches(context: ConditionContext<*>): Boolean {
        val connectionFactory = context.getBean(ConnectionFactory::class.java)
        return connectionFactory is PostgresqlConnectionFactory
    }
}

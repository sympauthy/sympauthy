package com.sympauthy.data.postgresql

import io.micronaut.context.condition.Condition
import io.micronaut.context.condition.ConditionContext
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import com.sympauthy.data.driverConnectionFactory
import io.r2dbc.spi.ConnectionFactory

class DefaultDataSourceIsPostgreSQL : Condition {

    override fun matches(context: ConditionContext<*>): Boolean {
        val connectionFactory = context.getBean(ConnectionFactory::class.java)
        return connectionFactory.driverConnectionFactory is PostgresqlConnectionFactory
    }
}

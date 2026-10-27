package com.sympauthy.data.h2

import io.micronaut.context.condition.Condition
import io.micronaut.context.condition.ConditionContext
import io.r2dbc.h2.H2ConnectionFactory
import com.sympauthy.data.driverConnectionFactory
import io.r2dbc.spi.ConnectionFactory

class DefaultDataSourceIsH2 : Condition {

    override fun matches(context: ConditionContext<*>): Boolean {
        val connectionFactory = context.getBean(ConnectionFactory::class.java)
        return connectionFactory.driverConnectionFactory is H2ConnectionFactory
    }
}

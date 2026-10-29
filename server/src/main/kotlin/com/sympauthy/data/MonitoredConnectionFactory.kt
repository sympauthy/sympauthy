package com.sympauthy.data

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import io.r2dbc.spi.Wrapped
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * The connection factory every repository obtains its connections from, reporting the database's
 * [Condition] as it goes: a connection obtained is a success, and one that cannot be is a failure.
 *
 * A connection that cannot be obtained is reported whatever the driver says went wrong, a refused password
 * included: the server cannot work without the database either way.
 */
class MonitoredConnectionFactory(
    private val delegate: ConnectionFactory,
    private val conditionMonitor: ConditionMonitor
) : ConnectionFactory, Wrapped<ConnectionFactory> {

    override fun create(): Publisher<out Connection> = Mono.from(delegate.create())
        .doOnNext { conditionMonitor.reportSuccess(Condition.Database) }
        .doOnError { failure -> conditionMonitor.reportFailure(Condition.Database, failure) }

    override fun getMetadata(): ConnectionFactoryMetadata = delegate.metadata

    override fun unwrap(): ConnectionFactory = delegate
}

/**
 * Puts a [MonitoredConnectionFactory] in front of every connection factory the context creates.
 */
@Singleton
class MonitoredConnectionFactoryListener(
    @Inject private val conditionMonitor: Provider<ConditionMonitor>
) : BeanCreatedEventListener<ConnectionFactory> {

    override fun onCreated(event: BeanCreatedEvent<ConnectionFactory>): ConnectionFactory =
        MonitoredConnectionFactory(event.bean, conditionMonitor.get())
}

/**
 * The connection factory the driver created, under every factory wrapped in front of it.
 */
val ConnectionFactory.driverConnectionFactory: ConnectionFactory
    get() {
        var factory = this
        while (factory is Wrapped<*>) {
            factory = factory.unwrap() as? ConnectionFactory ?: break
        }
        return factory
    }

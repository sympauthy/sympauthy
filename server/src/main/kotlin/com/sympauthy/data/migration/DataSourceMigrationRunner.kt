package com.sympauthy.data.migration

import io.micronaut.context.annotation.Context
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.naming.NameResolver
import io.micronaut.flyway.DataSourceMigrationRunner
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Inject
import java.util.*
import javax.sql.DataSource

/**
 * Runs flyway migration as soon as a [DataSource] is up.
 *
 * Looks like pure madness, however simply creating the DataSource using a Bean does not satisfy
 * the condition: event.getBeanDefinition() instanceof NameResolver.
 * And we must an official DataSourceMigrationRunner since we need the StaticResourceProvider for our
 * migration script when running with a native image.
 */
@Context
class DataSourceMigrationRunner(
    @Inject private val migrationRunner: DataSourceMigrationRunner
) : BeanCreatedEventListener<DataSource> {

    override fun onCreated(event: BeanCreatedEvent<DataSource>): DataSource {
        val fakeEvent = BeanCreatedEvent(
            event.source,
            object : BeanDefinition<DataSource> by event.beanDefinition, NameResolver {
                override fun resolveName(): Optional<String> = Optional.of(event.beanIdentifier.name)
            },
            event.beanIdentifier,
            event.bean,
        )
        return migrationRunner.onCreated(fakeEvent)
    }
}

package com.sympauthy.data.h2.migration

import com.sympauthy.data.h2.DefaultDataSourceIsH2
import com.sympauthy.data.migration.DriverFlywayConfigurationCustomizer
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import jakarta.inject.Named

@Context
@Named("default")
@Requires(condition = DefaultDataSourceIsH2::class)
class H2FlywayConfigurationCustomizer : DriverFlywayConfigurationCustomizer("h2")

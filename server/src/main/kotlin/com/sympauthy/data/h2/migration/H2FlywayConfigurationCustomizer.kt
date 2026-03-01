package com.sympauthy.data.h2.migration

import com.sympauthy.data.h2.DefaultDataSourceIsH2
import com.sympauthy.data.migration.DriverFlywayConfigurationCustomizer
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
@Named("default")
@ReflectiveAccess
@Requires(condition = DefaultDataSourceIsH2::class)
class H2FlywayConfigurationCustomizer : DriverFlywayConfigurationCustomizer("h2")

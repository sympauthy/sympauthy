package com.sympauthy

import com.sympauthy.cron.CleanAbandonedAccountCron
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.ApplicationContextConfigurer

/**
 * Leaves the server's scheduled jobs out of every context a test starts, for the reason
 * `docs/standards/testing-standard.md` gives.
 *
 * Registered as a service rather than applied by [com.sympauthy.data.Database], because every builder
 * loads one: a context `@MicronautTest` starts shares the `h2` database with the repository tests and
 * would sweep it the same way.
 *
 * The jobs are the `cron` package, named through one of them so that a move of the package moves this
 * with it. That the package holds every one of them is [UnscheduledJobsConfigurerTest]'s to prove.
 */
class UnscheduledJobsConfigurer : ApplicationContextConfigurer {

    override fun configure(builder: ApplicationContextBuilder) {
        builder.beansPredicate { bean -> !bean.name.startsWith(JOBS_PACKAGE) }
    }

    private companion object {

        val JOBS_PACKAGE = "${CleanAbandonedAccountCron::class.java.packageName}."
    }
}

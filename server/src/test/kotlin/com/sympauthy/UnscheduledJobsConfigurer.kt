package com.sympauthy

import com.sympauthy.cron.CleanAbandonedAccountCron
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.ApplicationContextConfigurer

/**
 * Leaves the server's scheduled jobs out of every context a test starts.
 *
 * A context carrying them runs them: each declares a fixed delay and no initial delay, so the startup
 * event schedules the first run for now — inside whichever test resolved the bean that started the
 * context. Each sweep then deletes against the live clock, which has moved past the constant a fixture
 * dates its rows from, so the test loses rows it is still using.
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

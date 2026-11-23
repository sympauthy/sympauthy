package com.sympauthy

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
 * The jobs are every class named `…Cron`, the suffix `docs/standards/general-code-standard.md` gives
 * one, wherever it sits: the sweeps are in `cron`, and a job keeping a mechanism of `platform` alive sits
 * beside that mechanism. That the suffix names every one of them is [UnscheduledJobsConfigurerTest]'s
 * to prove.
 */
class UnscheduledJobsConfigurer : ApplicationContextConfigurer {

    override fun configure(builder: ApplicationContextBuilder) {
        builder.beansPredicate { bean -> !bean.name.endsWith(JOB_SUFFIX) }
    }

    private companion object {

        const val JOB_SUFFIX = "Cron"
    }
}

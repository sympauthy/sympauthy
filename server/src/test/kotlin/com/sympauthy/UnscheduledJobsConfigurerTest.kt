package com.sympauthy

import com.sympauthy.data.Database
import io.micronaut.scheduling.annotation.Scheduled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Holds [UnscheduledJobsConfigurer] to the jobs a context wires: one it leaves in is scheduled for now
 * by whichever test starts that context, and sweeps the database every class in the run shares.
 *
 * What it catches is a job declared outside the package the configurer names, and a scheduled bean a
 * framework contributes with it.
 */
class UnscheduledJobsConfigurerTest {

    @ParameterizedTest
    @EnumSource(Database::class)
    fun `No job is scheduled in a context a test starts`(database: Database) {
        val scheduled = database.context.allBeanDefinitions
            .flatMap { definition -> definition.executableMethods }
            .filter { method -> method.getAnnotationValuesByType(Scheduled::class.java).isNotEmpty() }
            .map { method -> "${method.declaringType.name}.${method.methodName}" }
            .sorted()

        assertEquals(
            emptyList<String>(), scheduled,
            "These run inside whichever test starts this context, and take the rows it is still using. " +
                "One of ours belongs in the package the configurer names; a framework's is turned off by " +
                "its own key in application-test.yml."
        )
    }
}

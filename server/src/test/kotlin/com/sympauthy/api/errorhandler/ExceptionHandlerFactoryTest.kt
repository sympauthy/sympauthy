package com.sympauthy.api.errorhandler

import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.context.annotation.Replaces
import io.micronaut.http.server.exceptions.ExceptionHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Holds the exception handlers [ExceptionHandlerFactory] replaces to the ones the frameworks on the
 * classpath register.
 *
 * A handler left in place wins over ours for every failure of its type and answers in a body of its
 * own, with no `error_code` — so a dependency bringing a new one has no symptom but a caller who can
 * no longer branch on what they were told. Nothing else fails: the server starts, and every test
 * asserting a code this server names still passes.
 *
 * The bean definitions are read off the classpath rather than out of a started context, which would
 * need a datasource to start and would have resolved the replacements already.
 */
class ExceptionHandlerFactoryTest {

    @Test
    fun `Every exception handler a framework registers is replaced by one of ours`() {
        val (ours, theirs) = DefaultBeanDefinitionsProvider().provide(javaClass.classLoader)
            .filter { ExceptionHandler::class.java.isAssignableFrom(it.beanType) }
            .partition { it.beanType.packageName.startsWith("com.sympauthy.") }
        val replaced = ours.mapNotNull { it.classValue(Replaces::class.java).orElse(null) }.toSet()

        assertTrue(theirs.isNotEmpty(), "No framework handler was found, so the classpath was not read.")
        assertEquals(
            emptyList<String>(),
            theirs.map { it.beanType }.filterNot { it in replaced }.map { it.name }.sorted(),
            "These handlers answer their failures in a body of their own. Replace each in " +
                "ExceptionHandlerFactory with one for the same type, and give ExceptionNormalizer an " +
                "answer for it where a 500 is not one."
        )
    }
}

package com.sympauthy.config

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.EnvironmentPropertySource
import io.micronaut.context.env.SystemPropertiesPropertySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.FieldSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Holds the configuration files the server ships to the keys it declares: every key in them binds to a
 * property something reads.
 *
 * A file shipped with a key that binds to nothing takes readiness down on every deployment enabling that
 * environment, so the check the operator's file goes through is the same one these go through, and it
 * runs here rather than at the first startup after a release.
 *
 * No context is started for it. Building one and starting only its environment reads every property
 * source, and the keys are read off bean definitions the annotation processor wrote — so no bean is
 * instantiated, and nothing is asked of a database.
 */
class ShippedConfigurationKeysTest {

    @ParameterizedTest
    @FieldSource("environments")
    fun `Every key the shipped configuration writes binds`(environment: String) {
        val declaredKeys = DeclaredConfigurationKeys.ofTheServer()
        val context = ApplicationContext.builder().environments(environment).build()
        context.environment.start()

        val unboundKeys = context.environment.propertySources
            .filterNot { it is SystemPropertiesPropertySource || it is EnvironmentPropertySource }
            .flatMap { source ->
                source.filter(declaredKeys::answersFor)
                    .flatMap { key -> declaredKeys.findUnboundKeys(key, source[key]) }
                    .map { key -> "${source.origin.location()}: $key" }
            }

        assertEquals(
            emptyList<String>(), unboundKeys,
            "These keys are written in a file the server ships and bind to nothing, so a deployment " +
                "enabling this environment starts unready."
        )
    }

    @Test
    fun `Every environment the shipped configuration offers is named above`() {
        assertEquals(
            shippedEnvironments, environments.toSet(),
            "The environments this test runs and the ones the module ships have to be the same set: an " +
                "environment nobody names here is a file nothing above reads."
        )
    }

    companion object {

        /**
         * The environments the parameterized test runs. `application.yml` needs none of them named — it
         * is read whichever one is active.
         */
        @JvmStatic
        private val environments = listOf("default", "admin", "mail", "discord", "google")
    }
}

private const val CONFIGURATION = "src/main/resources"

private val FILE_NAME = Regex("""application-(.+)\.yml""")

/**
 * The module holding the files this test reads, found by walking up from the working directory so that a
 * run started above it still resolves.
 */
private val moduleRoot: Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
    .flatMap { sequenceOf(it, it.resolve("server")) }
    .firstOrNull { it.resolve(CONFIGURATION).isDirectory() }
    ?: error("No module holding $CONFIGURATION above ${Path.of("").toAbsolutePath()}.")

private val shippedEnvironments: Set<String> by lazy {
    Files.list(moduleRoot.resolve(CONFIGURATION)).use { paths ->
        paths.toList().mapNotNullTo(mutableSetOf()) { FILE_NAME.matchEntire(it.name)?.groupValues?.get(1) }
    }
}

package com.sympauthy.config

import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import com.sympauthy.business.model.security.GeoProvider
import com.sympauthy.business.model.security.IpProvider
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.AdvancedConfig
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The words a setting selecting an implementation accepts, read off what the container publishes.
 *
 * [BuiltTestImplementations] is what shows the reading builds none of them. The shipped strategy
 * would not: it injects only repositories, so building it raises nothing a test would notice.
 */
@MicronautTest(environments = ["default", "test", "h2"])
class PublishedImplementationReaderTest {

    @Inject
    lateinit var reader: PublishedImplementationReader

    /**
     * The map [com.sympauthy.business.manager.key.CryptoKeysManager] resolves a strategy out of.
     */
    @Inject
    lateinit var generationStrategies: Map<String, CryptoKeysGenerationStrategy>

    /**
     * The maps [com.sympauthy.api.util.SecurityContextUtil] resolves an edge out of, which are two
     * because an edge publishing no location is not a word the geo setting may name.
     */
    @Inject
    lateinit var ipProviders: Map<String, IpProvider>

    @Inject
    lateinit var geoProviders: Map<String, GeoProvider>

    @Inject
    lateinit var advancedConfig: AdvancedConfig

    @Test
    fun `read - Answer the qualifiers the implementations are published under`() {
        assertEquals(
            setOf("first", "second-2"),
            reader.read(PublishedImplementationTestPort::class).qualifiers
        )
    }

    @Test
    fun `read - Answer without building any of them`() {
        val builtBefore = BuiltTestImplementations.names

        reader.read(PublishedImplementationTestPort::class)

        assertEquals(builtBefore, BuiltTestImplementations.names)
    }

    @Test
    fun `read - Refuse an implementation published under no name`() {
        val failure = assertThrows<IllegalStateException> {
            reader.read(UnnamedImplementationTestPort::class)
        }

        assertEquals(
            "${UnnamedTestImplementation::class.java.name} implement " +
                "${UnnamedImplementationTestPort::class.java.name} and are published under no name, " +
                "so no word a deployment writes could select them.",
            failure.message
        )
    }

    @Test
    fun `read - Refuse an implementation published under a name a file would not spell`() {
        val failure = assertThrows<IllegalStateException> {
            reader.read(MisspeltQualifierTestPort::class)
        }

        assertEquals(
            "Auto_Increment name implementations of ${MisspeltQualifierTestPort::class.java.name} " +
                "and are not spelled the way a value in a configuration file is: a qualifier is " +
                "lowercase letters and digits separated by dashes.",
            failure.message
        )
    }

    @Test
    fun `read - Refuse an interface nothing implements`() {
        assertThrows<IllegalStateException> { reader.read(UnimplementedTestPort::class) }
    }

    @Test
    fun `read - Answer the names the container injects the implementations under`() {
        assertEquals(
            generationStrategies.keys.sorted(),
            reader.read(CryptoKeysGenerationStrategy::class).qualifiers.toList()
        )
    }

    @Test
    fun `read - Answer the names the container injects the edges under`() {
        assertEquals(ipProviders.keys.sorted(), reader.read(IpProvider::class).qualifiers.toList())
        assertEquals(geoProviders.keys.sorted(), reader.read(GeoProvider::class).qualifiers.toList())
    }

    /**
     * Renaming an implementation moves both sides of every assertion above at once, and leaves the
     * word `application-default.yml` writes behind. Nothing else reads the shipped file's value.
     */
    @Test
    fun `The shipped configuration selects implementations that are published`() {
        assertEquals(
            emptyList<String>(),
            advancedConfig.configurationErrors.orEmpty()
                .filterIsInstance<ConfigurationException>()
                .map { "${it.key} (${it.messageId})" },
            "The shipped advanced configuration did not parse, so a deployment running the defaults " +
                "starts unready."
        )
    }
}

/**
 * A port this test owns, so that a case about several implementations names a set no change to the
 * server's own settings moves. One of the two carries a digit and a dash, which is the whole of the
 * shape a qualifier may take.
 */
interface PublishedImplementationTestPort

@Singleton
@Named("first")
class FirstPublishedImplementation : PublishedImplementationTestPort {
    init {
        BuiltTestImplementations.record("first")
    }
}

@Singleton
@Named("second-2")
class SecondPublishedImplementation : PublishedImplementationTestPort {
    init {
        BuiltTestImplementations.record("second-2")
    }
}

/**
 * A port whose implementation carries no name. The container injects it into a
 * `Map<String, UnnamedImplementationTestPort>` under a name of its own devising, which is what makes
 * leaving it out of the published set a divergence rather than an omission.
 */
interface UnnamedImplementationTestPort

@Singleton
class UnnamedTestImplementation : UnnamedImplementationTestPort

/**
 * A port whose implementation is published under a name no configuration file would spell, which the
 * container passes through to the operator's YAML exactly as it was written.
 */
interface MisspeltQualifierTestPort

@Singleton
@Named("Auto_Increment")
class MisspeltTestImplementation : MisspeltQualifierTestPort

/**
 * A port nothing implements, which a setting selecting from it could name no word for.
 */
interface UnimplementedTestPort

/**
 * The implementations of [PublishedImplementationTestPort] the container has built. Written from
 * whichever thread built one, and read as a delta, so that a class resolving the port first leaves
 * the assertion above meaning what it says.
 */
object BuiltTestImplementations {

    private val built = CopyOnWriteArrayList<String>()

    val names: List<String> get() = built.toList()

    fun record(name: String) {
        built += name
    }
}

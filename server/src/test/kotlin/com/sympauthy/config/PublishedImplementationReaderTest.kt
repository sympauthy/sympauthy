package com.sympauthy.config

import com.sympauthy.business.model.key.CryptoKeysGenerationStrategy
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The words a setting selecting an implementation accepts, read off what the container publishes.
 *
 * [BuiltTestImplementations] is what proves the reading builds none of them: an implementation is
 * free to inject the very configuration being built, so a reader that instantiated one would be a
 * cycle no test of the shipped strategy — which injects only repositories — would show.
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

    @Test
    fun `read - Answer the qualifiers the implementations are published under`() {
        assertEquals(
            setOf("first", "second"),
            reader.read(PublishedImplementationTestPort::class).qualifiers
        )
    }

    @Test
    fun `read - Answer without building any of them`() {
        reader.read(PublishedImplementationTestPort::class)

        assertEquals(emptyList<String>(), BuiltTestImplementations.names)
    }

    @Test
    fun `read - Answer the names the container injects the implementations under`() {
        assertEquals(
            generationStrategies.keys.sorted(),
            reader.read(CryptoKeysGenerationStrategy::class).qualifiers.toList()
        )
    }
}

/**
 * A port this test owns, so that a case about several implementations names a set no change to the
 * server's own settings moves.
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
@Named("second")
class SecondPublishedImplementation : PublishedImplementationTestPort {
    init {
        BuiltTestImplementations.record("second")
    }
}

/**
 * The implementations of [PublishedImplementationTestPort] the container has built. Nothing else
 * asks for one, so anything in here was built by the reading under test.
 */
object BuiltTestImplementations {

    private val built = mutableListOf<String>()

    val names: List<String> get() = built.toList()

    fun record(name: String) {
        built += name
    }
}

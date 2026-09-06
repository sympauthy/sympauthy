package com.sympauthy.business.manager.securitycontext.edge

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.inject.BeanDefinitionReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Holds the set of extractions published for a proxy to the two things that read it: the
 * configuration, which admits exactly these names, and the file this server ships, which names one.
 *
 * Publishing one more `@Singleton` is the whole of adding a provider, and nothing enumerates them, so
 * these are what would otherwise be found by starting the server: two extractions answering to one
 * name, where the configuration would resolve to whichever the map kept, and a shipped default naming
 * an extraction that no longer exists.
 */
class PublishedEdgeProviderProfilesTest {

    @Test
    fun `Every extraction published has a name of its own`() {
        val names = publishedProfiles().map(EdgeProviderProfile::name)

        assertEquals(
            names.distinct().sorted(), names.sorted(),
            "These extractions answer to a name another one answers to as well, so a deployment " +
                "naming it reaches whichever of them the configuration happened to keep."
        )
    }

    @Test
    fun `The provider the shipped configuration names is published`() {
        val builder = ApplicationContext.builder().deduceEnvironment(false).environments("default")
        builder.build().use { context ->
            context.environment.start()
            val shipped = context.environment.getRequiredProperty(PROVIDER_KEY, String::class.java)

            assertTrue(
                publishedProfiles().any { it.name == shipped },
                "The configuration this server ships names the provider $shipped and no extraction " +
                    "is published under it, so every deployment starts unready."
            )
        }
    }

    /**
     * Every published extraction, read off the bean definitions the annotation processor wrote rather
     * than out of a started context: this answers before one is running, and a context started here
     * would build the datasource the question has nothing to do with.
     */
    private fun publishedProfiles(): List<EdgeProviderProfile> {
        val references = mutableListOf<BeanDefinitionReference<*>>()
        SoftServiceLoader.load(BeanDefinitionReference::class.java).collectAll(references)
        return references
            .filter { it.isPresent }
            .map { it.load() }
            .filter { EdgeProviderProfile::class.java.isAssignableFrom(it.beanType) }
            .map { it.beanType.getDeclaredConstructor().newInstance() as EdgeProviderProfile }
    }

    private companion object {
        const val PROVIDER_KEY = "advanced.security-context.provider"
    }
}

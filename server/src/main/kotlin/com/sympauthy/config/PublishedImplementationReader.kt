package com.sympauthy.config

import io.micronaut.context.BeanContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.reflect.KClass

/**
 * What the container publishes for an interface a setting selects an implementation from, which
 * `docs/config-layer-code-standard.md` makes the set of words that setting accepts.
 */
@Singleton
class PublishedImplementationReader(
    @Inject private val beanContext: BeanContext
) {

    /**
     * Every implementation of [type] the container publishes, or a failure where nothing implements
     * it, where one of them is published under no name, and where a name is not one an operator
     * could be asked to write.
     *
     * All three are the build being wrong rather than the file. An interface nothing implements
     * leaves the setting with no word at all; an implementation left unnamed is one the container
     * still injects — under a name of its own devising — into the map the manager resolves out of,
     * so a word that would run it is refused while the manager holds it all the same; and a name
     * the container passes through verbatim reaches a YAML file spelled however it was written.
     */
    fun <T : Any> read(type: KClass<T>): PublishedImplementations<T> {
        val definitions = beanContext.getBeanDefinitions(type.java)
        check(definitions.isNotEmpty()) {
            "Nothing implements ${type.java.name}, so no word a deployment writes to select an " +
                "implementation of it could name any."
        }
        val unnamed = definitions.filter { qualifierOfOrNull(it) == null }
        check(unnamed.isEmpty()) {
            "${unnamed.joinToString(", ") { it.beanType.name }} implement ${type.java.name} and are " +
                "published under no name, so no word a deployment writes could select them."
        }
        val qualifiers = definitions.mapNotNull(::qualifierOfOrNull)
        val misspelt = qualifiers.filterNot(QUALIFIER::matches)
        check(misspelt.isEmpty()) {
            "${misspelt.joinToString(", ")} name implementations of ${type.java.name} and are not " +
                "spelled the way a value in a configuration file is: a qualifier is lowercase " +
                "letters and digits separated by dashes."
        }
        return PublishedImplementations(type, qualifiers.toSortedSet())
    }

    /**
     * The name [definition] is published under, or null where it carries none.
     */
    private fun qualifierOfOrNull(definition: BeanDefinition<*>): String? =
        definition.annotationMetadata.stringValue(AnnotationUtil.NAMED).orElse(null)

    private companion object {

        /**
         * The shape of a qualifier, which is the shape of every other value an operator writes.
         */
        val QUALIFIER = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    }
}

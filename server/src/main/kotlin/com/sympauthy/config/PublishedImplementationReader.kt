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
     * Every implementation of [type] the container publishes, or a failure where one of them is
     * published under no name and where none of them is.
     *
     * Both are the build being wrong rather than the file. An interface nothing names leaves the
     * setting with no word an operator could write; an implementation left unnamed is one the
     * container still injects — under a name of its own devising — into the map the manager resolves
     * out of, so a word that would run it is refused while the manager holds it all the same.
     */
    fun <T : Any> read(type: KClass<T>): PublishedImplementations<T> {
        val definitions = beanContext.getBeanDefinitions(type.java)
        val unnamed = definitions.filter { qualifierOfOrNull(it) == null }
        check(unnamed.isEmpty()) {
            "${unnamed.joinToString(", ") { it.beanType.name }} implement ${type.java.name} and are " +
                "published under no name, so no word a deployment writes could select them."
        }
        check(definitions.isNotEmpty()) {
            "Nothing implements ${type.java.name}, so no word a deployment writes to select an " +
                "implementation of it could name any."
        }
        return PublishedImplementations(type, definitions.mapNotNull(::qualifierOfOrNull).toSortedSet())
    }

    /**
     * The name [definition] is published under, or null where it carries none.
     */
    private fun qualifierOfOrNull(definition: BeanDefinition<*>): String? =
        definition.annotationMetadata.stringValue(AnnotationUtil.NAMED).orElse(null)
}

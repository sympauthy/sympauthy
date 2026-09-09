package com.sympauthy.config

import io.micronaut.context.BeanContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.reflect.KClass

/**
 * What the container publishes for an interface a setting selects an implementation from.
 *
 * The set is read off the bean definitions and never off the beans: configuration is built before
 * the managers that read it, and an implementation is free to inject configuration itself, so
 * deciding whether a word is valid may not build what is behind it.
 *
 * The definitions are the ones the annotation processor wrote at compile time, which is what makes
 * the answer under a native image's closed world the same as the one on the JVM.
 */
@Singleton
class PublishedImplementationReader(
    @Inject private val beanContext: BeanContext
) {

    /**
     * Every implementation of [type] the container publishes, or a failure where that is none of
     * them. A setting selecting from an interface nothing is published under has no value an
     * operator could write at all, which is the build being wrong rather than the file.
     */
    fun <T : Any> read(type: KClass<T>): PublishedImplementations<T> {
        val qualifiers = beanContext.getBeanDefinitions(type.java)
            .mapNotNull(::qualifierOf)
            .toSortedSet()
        check(qualifiers.isNotEmpty()) {
            "No implementation of ${type.java.name} is published under a name, so no word a " +
                "deployment writes to select one could name any. This is the build being wrong " +
                "rather than the file."
        }
        return PublishedImplementations(type, qualifiers)
    }

    /**
     * The name [definition] is published under, or null where it carries none. An implementation
     * with no name is left out of the set: there is no word for a deployment to select it with, and
     * the manager that would run it is handed its implementations by name too.
     */
    private fun qualifierOf(definition: BeanDefinition<*>): String? =
        definition.annotationMetadata.stringValue(AnnotationUtil.NAMED).orElse(null)
}

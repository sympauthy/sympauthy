package com.sympauthy.config

import com.sympauthy.Application
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import jakarta.inject.Singleton

/**
 * The keys the server's own configuration domains declare, read off the bean definitions the annotation
 * processor wrote for them.
 *
 * Those are generated at compile time, so what comes back is closed by construction rather than by
 * anything remembering to register on a list. It is also only the server's own: a framework compiles its
 * configuration classes with the property lookups inlined rather than declared, so there is no list of
 * what `micronaut` or `flyway` accepts to be read here at all.
 */
@Singleton
class DeclaredConfigurationKeyReader {

    /**
     * Every key the server declares, or a failure where that is none of them. No configuration key on
     * the classpath is the build being wrong rather than the file, and a silent nothing here would read
     * exactly like a file with nothing wrong in it.
     */
    fun read(): List<DeclaredConfigurationKey> {
        val keys = serverBeanDefinitions().flatMap(::keysOf)
        check(keys.isNotEmpty()) {
            "No configuration key was found on the classpath, so nothing an operator writes could be " +
                "judged against one. This is the build being wrong rather than the file."
        }
        return keys
    }

    /**
     * The bean definitions the server's own classes were compiled into. The definition's own name is what
     * the classpath is filtered on, which is a string the reference already holds — asking for the bean
     * type instead would load every class a framework declares only to discard it again.
     *
     * The definitions are loaded the way the bean context loads them for itself, rather than asked of a
     * context: this answers before one is started, and it answers for a domain a requirement would have
     * disabled, which declares its keys either way.
     */
    fun serverBeanDefinitions(): List<BeanDefinition<*>> {
        val references = mutableListOf<BeanDefinitionReference<*>>()
        SoftServiceLoader.load(BeanDefinitionReference::class.java).collectAll(references)
        return references
            .filter { it.beanDefinitionName.startsWith(SERVER_PACKAGE) && it.isPresent }
            .map { it.load() }
    }

    /**
     * Every key [definition] declares. A map is declared as the subtree it opens, because what an
     * operator writes under it is theirs to name.
     */
    fun keysOf(definition: BeanDefinition<*>): List<DeclaredConfigurationKey> {
        if (!definition.annotationMetadata.hasAnnotation(ConfigurationReader::class.java)) return emptyList()
        val declarations =
            definition.injectedMethods.map { it.annotationMetadata to it.arguments.firstOrNull()?.type } +
                definition.injectedFields.map { it.annotationMetadata to it.asArgument().type } +
                definition.executableMethods.map { it.annotationMetadata to it.returnType.type }
        return declarations.mapNotNull { (metadata, type) ->
            val name = metadata.stringValue(Property::class.java, "name").orElse(null)
            when {
                name == null -> null
                type != null && Map::class.java.isAssignableFrom(type) -> DeclaredConfigurationKey.subtreeUnder(name)
                else -> DeclaredConfigurationKey(name)
            }
        }
    }

    private companion object {

        /**
         * What a bean definition the server itself declared is named under. It is taken from
         * [Application.PACKAGE] rather than written out, so nothing here has to be told when the
         * server is renamed: a filter naming the package by hand would go on compiling and answer
         * that the server declares no configuration at all.
         */
        val SERVER_PACKAGE = "${Application.PACKAGE}."
    }
}

package com.sympauthy.config

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.inject.BeanDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Holds every key one of the server's configuration properties classes declares under the prefix of the
 * class declaring it.
 *
 * `@ConfigurationProperties` anchors a nested class to the prefix of the class it is nested in, and the
 * key a field binds to is derived from the prefix of the field's own type. Nest one annotated interface
 * in one owner and reuse it as the type of a field in another, and the second owner's field binds under
 * the first owner's prefix: the key an operator writes under the second is read by nothing, and nothing
 * about it fails to compile. `templates.clients.*.authorization-webhook` was that key.
 *
 * This is the class of defect rather than the instance, which is why it is held here and not by
 * [ShippedConfigurationKeysTest] — a key the server ships nowhere is one no shipped file names.
 */
class ConfigurationPropertiesPrefixTest {

    @Test
    fun `Every key a configuration properties class declares is under its own prefix`() {
        val misanchored = DeclaredConfigurationKeys.serverBeanDefinitions()
            .filter { it.annotationMetadata.hasAnnotation(ConfigurationReader::class.java) }
            .flatMap { definition ->
                val prefix = prefixOf(definition)
                DeclaredConfigurationKeys.patternsOf(definition)
                    .filterNot { it.startsWith("$prefix.") }
                    .map { "${definition.beanType.simpleName} declares $it under $prefix" }
            }

        assertEquals(
            emptyList<String>(), misanchored.sorted(),
            "These properties bind under a prefix other than the one their class is anchored to, so " +
                "what an operator writes under that class is read by nothing."
        )
    }
}

private fun prefixOf(definition: BeanDefinition<*>): String = definition.annotationMetadata
    .stringValue(ConfigurationReader::class.java, "prefix")
    .orElseThrow { AssertionError("${definition.beanType.name} is a configuration reader with no prefix.") }

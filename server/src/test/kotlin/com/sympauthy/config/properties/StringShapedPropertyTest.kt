package com.sympauthy.config.properties

import com.sympauthy.compiledClassesOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties

/**
 * Every property of a properties class is string-shaped, the rule
 * `docs/standards/config-layer-code-standard.md` states.
 */
class StringShapedPropertyTest {

    private val packageName = StringShapedPropertyTest::class.java.packageName

    @Test
    fun `Every property of a properties class is string-shaped`() {
        val types = compiledClassesOf(packageName).filterNot { it.kotlin.isCompanion }
        assertTrue(types.isNotEmpty(), "No properties class was found to check.")

        val converted = types.flatMap { type ->
            type.kotlin.declaredMemberProperties
                .filterNot { isStringShaped(it.returnType) }
                .map { "${type.name}.${it.name}: ${it.returnType}" }
        }

        assertTrue(converted.isEmpty(), "Converted by Micronaut instead of the parser: ${converted.joinToString()}")
    }

    /**
     * Whether Micronaut hands [type] to the parser as it was written: a string, a list or a map of
     * string-shaped values, an `Any` it does not convert, or another properties type of the package.
     */
    private fun isStringShaped(type: KType): Boolean {
        val classifier = type.classifier as? KClass<*> ?: return false
        return when (classifier) {
            String::class, Any::class -> true
            List::class, Map::class -> type.arguments.all { argument -> argument.type?.let(::isStringShaped) ?: true }
            else -> classifier.java.packageName == packageName
        }
    }
}

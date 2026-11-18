package com.sympauthy.config.properties

import com.sympauthy.compiledClassesOf
import io.micronaut.core.annotation.Nullable
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaGetter

/**
 * Every nullable property of a properties interface carries `@get:Nullable`, the rule
 * `docs/standards/config-layer-code-standard.md` states.
 */
class NullableGetterTest {

    @Test
    fun `Every nullable property of a properties interface is annotated nullable on its getter`() {
        val interfaces = compiledClassesOf(NullableGetterTest::class.java.packageName).filter { it.isInterface }
        assertTrue(interfaces.isNotEmpty(), "No properties interface was found to check.")

        val unannotated = interfaces.flatMap { type ->
            type.kotlin.declaredMemberProperties
                .filter { it.returnType.isMarkedNullable }
                .filter { it.javaGetter?.isAnnotationPresent(Nullable::class.java) != true }
                .map { "${type.name}.${it.name}" }
        }

        assertTrue(unannotated.isEmpty(), "Missing @get:Nullable on: ${unannotated.joinToString()}")
    }
}

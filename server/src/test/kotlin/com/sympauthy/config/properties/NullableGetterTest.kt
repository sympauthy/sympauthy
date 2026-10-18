package com.sympauthy.config.properties

import com.sympauthy.compiledClassesOf
import io.micronaut.core.annotation.Nullable
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaGetter

/**
 * Every nullable property of a properties interface carries `@get:Nullable`.
 *
 * The implementation of such an interface is generated, and KSP does not carry Kotlin's nullability
 * into it: a property declared `String?` and left out of the configuration fails the bean with
 * "No property found" instead of answering null. The annotation is what the generated implementation
 * reads. A class bound through its setters is not concerned, since nothing is generated for its
 * getters.
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

package com.sympauthy.api.controller

import io.micronaut.http.annotation.Part
import io.swagger.v3.oas.annotations.media.Schema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Holds the name a form field is published under to the name its controller binds it by, on every
 * controller of every surface.
 */
class FormFieldNameTest {

    @Test
    fun `Every form field is published under the name it is bound by`() {
        val fields = controllerClasses().flatMap { controller ->
            controller.declaredMethods.flatMap { method ->
                method.parameterAnnotations.mapNotNull { annotations ->
                    val part = annotations.filterIsInstance<Part>().singleOrNull() ?: return@mapNotNull null
                    val schema = annotations.filterIsInstance<Schema>().singleOrNull()
                    "${controller.simpleName}.${method.name}" to (part.value to schema?.name)
                }
            }
        }

        assertTrue(fields.isNotEmpty(), "no controller binds a form field, so this test reads nothing")
        fields.forEach { (operation, names) ->
            val (bound, published) = names
            assertEquals(bound, published, "$operation binds `$bound` and publishes it as `$published`")
        }
    }

    /**
     * The controllers compiled under [CONTROLLER_PACKAGE], read off every classpath root holding that package:
     * the test output declares the same package, and is not where a controller lives.
     */
    private fun controllerClasses(): List<Class<*>> =
        javaClass.classLoader.getResources(CONTROLLER_PACKAGE).toList()
            .map { File(it.toURI()) }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.name.endsWith("Controller.class") && '$' !in it.name }
                    .map { file ->
                        val name = file.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
                        Class.forName("${CONTROLLER_PACKAGE.replace('/', '.')}.$name")
                    }
            }

    private companion object {
        const val CONTROLLER_PACKAGE = "com/sympauthy/api/controller"
    }
}

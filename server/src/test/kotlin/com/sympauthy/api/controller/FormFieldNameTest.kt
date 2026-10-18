package com.sympauthy.api.controller

import com.sympauthy.compiledClassesOf
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.swagger.v3.oas.annotations.media.Schema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Holds the name a form field is published under to the name its controller binds it by, on every
 * controller of every surface.
 */
class FormFieldNameTest {

    @Test
    fun `Every form field is published under the name it is bound by`() {
        val controllers = compiledClassesOf(FormFieldNameTest::class.java.packageName)
            .filter { it.isAnnotationPresent(Controller::class.java) }
        val fields = controllers.flatMap { controller ->
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
}

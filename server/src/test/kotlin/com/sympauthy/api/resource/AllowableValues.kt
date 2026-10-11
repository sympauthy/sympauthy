package com.sympauthy.api.resource

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

/**
 * The values [property] of [resource] declares it admits, in the order the document lists them: those
 * of its own schema, or of its items where it is a list declared with an [ArraySchema].
 *
 * Read off the getter, which is where a resource writes its schema: `@get:Schema` is what survives to the
 * property the document is generated from.
 */
internal fun allowableValuesOf(resource: KClass<*>, property: String): List<String> {
    val getter = resource.memberProperties.single { it.name == property }.javaGetter
    val values = getter?.getAnnotation(ArraySchema::class.java)?.schema?.allowableValues
        ?: getter?.getAnnotation(Schema::class.java)?.allowableValues
    return values?.toList().orEmpty()
}

package com.sympauthy.api.resource.admin

import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStatus
import com.sympauthy.business.model.flow.InteractiveFlowSessionStatus
import com.sympauthy.util.wireName
import io.swagger.v3.oas.annotations.media.Schema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter

/**
 * Holds the sets the admin interactive-flow resources publish to the enums they are published from.
 *
 * The values reach the document as `allowableValues`, which is a literal: nothing about adding a value to one
 * of these enums makes a compiler ask for the string. The generated client turns each set into a closed enum
 * of its own, so a value missing from one of these lists is a session a client cannot deserialize and a
 * filter that refuses a value this server answers with.
 */
class AdminInteractiveFlowSessionAllowableValuesTest {

    @Test
    fun `Every purpose is published where a purpose is published`() {
        assertEquals(
            InteractiveFlowPurpose.entries.map { it.wireName },
            allowableValuesOf(AdminInteractiveFlowPurposeResource::class, "value")
        )
    }

    @Test
    fun `Every session status is published on the listing row`() {
        assertEquals(
            InteractiveFlowSessionStatus.entries.map { it.wireName },
            allowableValuesOf(AdminInteractiveFlowSessionSummaryResource::class, "status")
        )
    }

    @Test
    fun `Every session status is published on the detail`() {
        assertEquals(
            InteractiveFlowSessionStatus.entries.map { it.wireName },
            allowableValuesOf(AdminInteractiveFlowSessionDetailResource::class, "status")
        )
    }

    @Test
    fun `Every purpose status is published on a purpose entry`() {
        assertEquals(
            InteractiveFlowPurposeStatus.entries.map { it.wireName },
            allowableValuesOf(AdminInteractiveFlowSessionPurposeProgressResource::class, "status")
        )
    }

    /**
     * The values [property] of [resource] declares it admits, in the order the document lists them.
     *
     * Read off the getter, which is where the annotation is written: a resource declares its schema on
     * `@get:Schema` so the annotation survives to the property the document is generated from.
     */
    private fun allowableValuesOf(resource: KClass<*>, property: String): List<String> {
        val getter = resource.memberProperties.single { it.name == property }.javaGetter
        val schema = getter?.getAnnotation(Schema::class.java)
            ?: resource.memberProperties.single { it.name == property }.findAnnotation<Schema>()
        return schema?.allowableValues?.toList().orEmpty()
    }
}

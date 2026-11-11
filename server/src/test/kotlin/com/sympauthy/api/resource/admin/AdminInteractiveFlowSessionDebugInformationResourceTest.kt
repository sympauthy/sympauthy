package com.sympauthy.api.resource.admin

import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime

/**
 * `value` is declared as `Any?`, so what reaches the wire is chosen by the type it holds at runtime rather than
 * by its declaration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminInteractiveFlowSessionDebugInformationResourceTest {

    // The inclusion application.yml sets: the default would drop an empty list, which is a value here.
    private val mapper = ObjectMapper.create(mapOf("micronaut.serde.serialization.inclusion" to "NON_NULL"))

    @AfterAll
    fun closeMapper() = mapper.close()

    @Test
    fun `Every value is written as its type says`() {
        listOf(
            entry("text", "client") to """{"code":"c","type":"text","value":"client"}""",
            entry("text_list", listOf("openid", "profile")) to
                """{"code":"c","type":"text_list","value":["openid","profile"]}""",
            entry("text_list", emptyList<String>()) to """{"code":"c","type":"text_list","value":[]}""",
            entry("date", LocalDateTime.of(2026, 11, 11, 13, 33, 56)) to
                """{"code":"c","type":"date","value":"2026-11-11T13:33:56"}""",
            entry("presence", false) to """{"code":"c","type":"presence","value":false}""",
            entry("text", null) to """{"code":"c","type":"text"}"""
        ).forEach { (resource, json) ->
            assertEquals(json, mapper.writeValueAsString(resource))
        }
    }

    private fun entry(type: String, value: Any?) = AdminInteractiveFlowSessionDebugInformationResource(
        code = "c",
        type = type,
        value = value
    )
}

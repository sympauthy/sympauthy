package com.sympauthy.api.resource.error

import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OAuth2ErrorResourceTest {

    private val objectMapper = ObjectMapper.getDefault()

    @Test
    fun `A null description or details is absent from the body`() {
        val resource = OAuth2ErrorResource(errorCode = "invalid_grant")

        val serialized = serialize(resource)

        assertEquals(setOf("error"), serialized.keys)
    }

    @Suppress("UNCHECKED_CAST")
    private fun serialize(resource: OAuth2ErrorResource): Map<String, Any> =
        objectMapper.readValue(objectMapper.writeValueAsString(resource), Map::class.java) as Map<String, Any>
}

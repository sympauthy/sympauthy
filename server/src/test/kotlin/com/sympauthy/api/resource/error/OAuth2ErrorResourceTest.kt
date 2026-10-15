package com.sympauthy.api.resource.error

import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OAuth2ErrorResourceTest {

    private val objectMapper = ObjectMapper.getDefault()

    @Test
    fun `The code and the description are serialized as the members RFC 6749 defines`() {
        val resource = OAuth2ErrorResource(
            errorCode = "invalid_grant",
            details = "The authorization code has expired.",
            description = "Please go back to your application and start again."
        )

        val serialized = serialize(resource)

        assertEquals(setOf("error", "error_description", "details"), serialized.keys)
        assertEquals("invalid_grant", serialized["error"])
        assertEquals("Please go back to your application and start again.", serialized["error_description"])
        assertEquals("The authorization code has expired.", serialized["details"])
    }

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

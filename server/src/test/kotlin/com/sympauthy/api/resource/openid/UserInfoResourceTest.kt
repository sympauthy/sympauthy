package com.sympauthy.api.resource.openid

import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserInfoResourceTest {

    private val objectMapper = ObjectMapper.getDefault()

    @Test
    fun `A claim this resource declares no property for is serialized beside the ones it does`() {
        val resource = userInfo(additionalClaims = mapOf("loyalty_tier" to "gold", "employee_id_verified" to true))

        val serialized = serialize(resource)

        assertEquals("gold", serialized["loyalty_tier"])
        assertEquals(true, serialized["employee_id_verified"])
        assertEquals("ada@example.com", serialized["email"])
    }

    @Test
    fun `A resource carrying no such claim serializes the declared properties alone`() {
        val resource = userInfo(additionalClaims = emptyMap())

        val serialized = serialize(resource)

        assertEquals(setOf("sub", "email"), serialized.keys)
    }

    @Suppress("UNCHECKED_CAST")
    private fun serialize(resource: UserInfoResource): Map<String, Any> =
        objectMapper.readValue(objectMapper.writeValueAsString(resource), Map::class.java) as Map<String, Any>

    private fun userInfo(additionalClaims: Map<String, Any>) = UserInfoResource(
        sub = "07b1d3f0-0e08-4c2f-9c4a-6a4b3f9f9b44",
        name = null,
        givenName = null,
        familyName = null,
        middleName = null,
        nickname = null,
        preferredUsername = null,
        profile = null,
        picture = null,
        website = null,
        email = "ada@example.com",
        emailVerified = null,
        gender = null,
        birthDate = null,
        zoneInfo = null,
        locale = null,
        phoneNumber = null,
        phoneNumberVerified = null,
        address = null,
        updatedAt = null,
        additionalClaims = additionalClaims
    )
}

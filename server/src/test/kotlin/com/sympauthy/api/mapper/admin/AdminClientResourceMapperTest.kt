package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.EnabledScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdminClientResourceMapperTest {

    private val mapper = AdminClientResourceMapper()

    private fun client(allowedScopes: Set<EnabledScope>?) = Client(
        id = "my-app",
        secret = "secret",
        audience = Audience(id = "default", tokenAudience = "default"),
        allowedGrantTypes = setOf(GrantType.AUTHORIZATION_CODE),
        authorizationFlow = null,
        allowedScopes = allowedScopes
    )

    @Test
    fun `toResource - Leave allowed scopes absent when the configuration does not restrict them`() {
        val resource = mapper.toResource(client(allowedScopes = null))

        assertNull(resource.allowedScopes)
    }

    @Test
    fun `toResource - Publish allowed scopes as empty when the configuration allows none`() {
        val resource = mapper.toResource(client(allowedScopes = emptySet()))

        assertEquals(emptyList<String>(), resource.allowedScopes)
    }

    @Test
    fun `toResource - Publish the allowed scopes the configuration names`() {
        val resource = mapper.toResource(client(allowedScopes = setOf(ConsentableUserScope("profile"))))

        assertEquals(listOf("profile"), resource.allowedScopes)
    }
}

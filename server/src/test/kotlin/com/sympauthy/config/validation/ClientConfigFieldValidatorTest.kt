package com.sympauthy.config.validation

import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.config.ConfigParsingContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ClientConfigFieldValidatorTest {

    private val validator = ClientConfigFieldValidator()

    private val ctx = ConfigParsingContext()

    private val scopesById = listOf(
        GrantableUserScope("openid", publishedIn = emptySet()),
        ConsentableUserScope("profile"),
        ClientScope("users:claims:read")
    ).associateBy { it.scope }

    @Test
    fun `validateDefaultScopes - Refuse a client scope against the index it was written at`() {
        validator.validateDefaultScopes(
            ctx, "clients.my-app.default-scopes", listOf("openid", "users:claims:read"), scopesById
        )

        assertEquals(listOf("config.client.default_scopes.not_user_scope"), ctx.errors.map { it.messageId })
        assertEquals(listOf("clients.my-app.default-scopes[1]"), ctx.errors.map { it.key })
        assertEquals("users:claims:read", ctx.errors.first().values["scope"])
    }

    @Test
    fun `validateDefaultScopes - Return the user scopes it was given`() {
        val result = validator.validateDefaultScopes(
            ctx, "clients.my-app.default-scopes", listOf("openid", "profile"), scopesById
        )

        assertFalse(ctx.hasErrors)
        assertEquals(listOf("openid", "profile"), result?.map { it.scope })
    }
}

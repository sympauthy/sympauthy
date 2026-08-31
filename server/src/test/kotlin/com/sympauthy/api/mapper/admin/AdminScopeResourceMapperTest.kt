package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.DisabledScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminScopeResourceMapperTest {

    private val mapper = AdminScopeResourceMapper()

    private fun claim(id: String): Claim = Claim(
        id = id,
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = true,
        allowedValues = null,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = null,
                readableByUser = true,
                writableByUser = true,
                readableByClient = true,
                writableByClient = true
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = emptyList(),
                writableWithClientScopes = emptyList()
            )
        )
    )

    @Test
    fun `toResource - Report a scope the server serves`() {
        val resource = mapper.toResource(ConsentableUserScope("email"), listOf(claim("email")))

        assertEquals("email", resource.id)
        assertEquals("consentable", resource.type)
        assertEquals("openid", resource.origin)
        assertTrue(resource.enabled)
        assertEquals(listOf("email"), resource.claims)
    }

    @Test
    fun `toResource - Report a scope the deployment turned off`() {
        val resource = mapper.toResource(
            DisabledScope("my-scope", type = ScopeType.GRANTABLE),
            emptyList()
        )

        assertEquals("my-scope", resource.id)
        assertEquals("grantable", resource.type)
        assertEquals("custom", resource.origin)
        assertFalse(resource.enabled)
    }

    @Test
    fun `toResource - Carry the claims of a consentable scope, and no claims of another type`() {
        val disabledConsentable = mapper.toResource(
            DisabledScope("phone", type = ScopeType.CONSENTABLE),
            emptyList()
        )
        val grantable = mapper.toResource(
            GrantableUserScope("openid", discoverable = true),
            listOf(claim("sub"))
        )
        val client = mapper.toResource(ClientScope("users:read"), listOf(claim("sub")))

        assertEquals(emptyList<String>(), disabledConsentable.claims)
        assertNull(grantable.claims)
        assertNull(client.claims)
    }
}

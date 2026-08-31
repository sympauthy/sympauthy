package com.sympauthy.api.mapper.admin

import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.DisabledScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminScopeResourceMapperTest {

    private val mapper = AdminScopeResourceMapper()

    private val nameClaim = Claim(
        id = "name",
        enabled = true,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = false,
        allowedValues = null,
        acl = ClaimAcl(
            consent = ConsentAcl(
                scope = "profile",
                readableByUser = true,
                writableByUser = true,
                readableByClient = false,
                writableByClient = false
            ),
            unconditional = UnconditionalAcl(emptyList(), emptyList())
        )
    )

    @Test
    fun `toResource - Report a scope this server serves as enabled`() {
        val resource = mapper.toResource(ConsentableUserScope("profile"), listOf(nameClaim))

        assertEquals("profile", resource.id)
        assertEquals("consentable", resource.type)
        assertEquals("openid", resource.origin)
        assertTrue(resource.enabled)
        assertEquals(listOf("name"), resource.claims)
    }

    @Test
    fun `toResource - Report a scope the deployment turned off as disabled`() {
        val resource = mapper.toResource(DisabledScope("profile", ScopeType.CONSENTABLE), emptyList())

        assertEquals("profile", resource.id)
        assertEquals("consentable", resource.type)
        assertFalse(resource.enabled)
    }

    @Test
    fun `toResource - Report the type of a grantable scope`() {
        val resource = mapper.toResource(GrantableUserScope("openid", discoverable = true), emptyList())

        assertEquals("grantable", resource.type)
        assertNull(resource.claims)
    }

    @Test
    fun `toResource - Report the type of a client scope`() {
        val resource = mapper.toResource(ClientScope("users:read"), emptyList())

        assertEquals("client", resource.type)
        assertEquals("system", resource.origin)
        assertNull(resource.claims)
    }
}

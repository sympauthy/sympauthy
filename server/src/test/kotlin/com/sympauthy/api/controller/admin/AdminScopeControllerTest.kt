package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminScopeResourceMapper
import com.sympauthy.api.resource.admin.AdminScopeResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.DEFAULT_PAGE_SIZE
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.user.claim.Claim
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminScopeControllerTest {

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var scopeMapper: AdminScopeResourceMapper

    @InjectMockKs
    lateinit var controller: AdminScopeController

    private fun mockClaim(id: String): Claim = mockk {
        every { this@mockk.id } returns id
    }

    private fun mockResource(id: String, type: String, claims: List<String>? = null): AdminScopeResource =
        AdminScopeResource(
            id = id,
            type = type,
            origin = "openid",
            enabled = true,
            claims = claims
        )

    @Test
    fun `listScopes - Return paginated list with defaults`() = runTest {
        val profile = ConsentableUserScope("profile")
        val openid = GrantableUserScope("openid", discoverable = true)
        val scopes = listOf(profile, openid)

        val profileClaims = listOf(mockClaim("name"), mockClaim("family_name"))
        val profileResource = mockResource("profile", "consentable", listOf("name", "family_name"))
        val openidResource = mockResource("openid", "grantable")

        coEvery { scopeManager.listScopes() } returns scopes
        every { scopeManager.listClaimsProtectedByScope(openid) } returns emptyList()
        every { scopeManager.listClaimsProtectedByScope(profile) } returns profileClaims
        every { scopeMapper.toResource(openid, emptyList()) } returns openidResource
        every { scopeMapper.toResource(profile, profileClaims) } returns profileResource

        val result = controller.listScopes(null, null, null, null)

        assertEquals(DEFAULT_PAGE, result.page)
        assertEquals(DEFAULT_PAGE_SIZE, result.size)
        assertEquals(2, result.total)
        assertEquals(2, result.scopes.size)
        // Sorted by scope id: openid < profile
        assertSame(openidResource, result.scopes[0])
        assertSame(profileResource, result.scopes[1])
    }

    @Test
    fun `listScopes - Filter by type consentable`() = runTest {
        val profile = ConsentableUserScope("profile")
        val openid = GrantableUserScope("openid", discoverable = true)

        val profileClaims = listOf(mockClaim("name"))
        val profileResource = mockResource("profile", "consentable", listOf("name"))

        coEvery { scopeManager.listScopes() } returns listOf(profile, openid)
        every { scopeManager.listClaimsProtectedByScope(profile) } returns profileClaims
        every { scopeMapper.toResource(profile, profileClaims) } returns profileResource

        val result = controller.listScopes(null, null, "consentable", null)

        assertEquals(1, result.total)
        assertEquals(1, result.scopes.size)
        assertSame(profileResource, result.scopes[0])
    }

    @Test
    fun `listScopes - Filter by type grantable`() = runTest {
        val profile = ConsentableUserScope("profile")
        val openid = GrantableUserScope("openid", discoverable = true)

        val openidResource = mockResource("openid", "grantable")

        coEvery { scopeManager.listScopes() } returns listOf(profile, openid)
        every { scopeManager.listClaimsProtectedByScope(openid) } returns emptyList()
        every { scopeMapper.toResource(openid, emptyList()) } returns openidResource

        val result = controller.listScopes(null, null, "grantable", null)

        assertEquals(1, result.total)
        assertEquals(1, result.scopes.size)
        assertSame(openidResource, result.scopes[0])
    }

    @Test
    fun `listScopes - Filter by type client`() = runTest {
        val profile = ConsentableUserScope("profile")
        val usersRead = ClientScope("users:read")

        val usersReadResource = mockResource("users:read", "client")

        coEvery { scopeManager.listScopes() } returns listOf(profile, usersRead)
        every { scopeManager.listClaimsProtectedByScope(usersRead) } returns emptyList()
        every { scopeMapper.toResource(usersRead, emptyList()) } returns usersReadResource

        val result = controller.listScopes(null, null, "client", null)

        assertEquals(1, result.total)
        assertEquals(1, result.scopes.size)
        assertSame(usersReadResource, result.scopes[0])
    }

    @Test
    fun `listScopes - Unknown type returns empty list`() = runTest {
        val profile = ConsentableUserScope("profile")

        coEvery { scopeManager.listScopes() } returns listOf(profile)

        val result = controller.listScopes(null, null, "unknown", null)

        assertEquals(0, result.total)
        assertTrue(result.scopes.isEmpty())
    }

    @Test
    fun `listScopes - Apply page and size`() = runTest {
        val scopes = listOf(
            ConsentableUserScope("address"),
            ConsentableUserScope("email"),
            GrantableUserScope("openid", discoverable = true),
            ConsentableUserScope("phone"),
            ConsentableUserScope("profile")
        )
        val resources = scopes.map { mockResource(it.scope, "consentable") }

        coEvery { scopeManager.listScopes() } returns scopes
        scopes.forEachIndexed { i, scope ->
            every { scopeManager.listClaimsProtectedByScope(scope) } returns emptyList()
            every { scopeMapper.toResource(scope, emptyList()) } returns resources[i]
        }

        val result = controller.listScopes(1, 2, null, null)

        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(5, result.total)
        assertEquals(2, result.scopes.size)
    }

    @Test
    fun `listScopes - Return empty page when page exceeds total`() = runTest {
        val scope = ConsentableUserScope("profile")
        coEvery { scopeManager.listScopes() } returns listOf(scope)

        val result = controller.listScopes(5, 20, null, null)

        assertEquals(5, result.page)
        assertEquals(1, result.total)
        assertTrue(result.scopes.isEmpty())
    }

    @Test
    fun `listScopes - Claims populated for consentable, null for others`() = runTest {
        val profile = ConsentableUserScope("profile")
        val openid = GrantableUserScope("openid", discoverable = true)

        val profileClaims = listOf(mockClaim("name"))

        coEvery { scopeManager.listScopes() } returns listOf(profile, openid)
        every { scopeManager.listClaimsProtectedByScope(profile) } returns profileClaims
        every { scopeManager.listClaimsProtectedByScope(openid) } returns emptyList()
        every { scopeMapper.toResource(profile, profileClaims) } returns AdminScopeResource(
            id = "profile", type = "consentable", origin = "openid", enabled = true,
            claims = listOf("name")
        )
        every { scopeMapper.toResource(openid, emptyList()) } returns AdminScopeResource(
            id = "openid", type = "grantable", origin = "openid", enabled = true,
            claims = null
        )

        val result = controller.listScopes(null, null, null, null)

        assertNotNull(result.scopes.first { it.id == "profile" }.claims)
        assertNull(result.scopes.first { it.id == "openid" }.claims)
    }
}

package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminScopeResourceMapper
import com.sympauthy.api.resource.admin.AdminScopeResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.DisabledScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.ScopeType
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

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminScopeController

    private fun mockResource(
        id: String,
        type: String,
        claims: List<String>? = null,
        enabled: Boolean = true
    ): AdminScopeResource =
        AdminScopeResource(
            id = id,
            type = type,
            origin = "openid",
            enabled = enabled,
            claims = claims
        )

    @Test
    fun `listScopes - Return paginated list with defaults`() = runTest {
        val profile = ConsentableUserScope("profile")
        val openid = GrantableUserScope("openid", discoverable = true)
        val scopes = listOf(profile, openid)

        val profileClaims = listOf(mockk<Claim>(), mockk<Claim>())
        val profileResource = mockResource("profile", "consentable", listOf("name", "family_name"))
        val openidResource = mockResource("openid", "grantable")

        coEvery { scopeManager.listAllScopes() } returns scopes
        coEvery { scopeManager.listClaimsProtectedByScope(openid) } returns emptyList()
        coEvery { scopeManager.listClaimsProtectedByScope(profile) } returns profileClaims
        every { scopeMapper.toResource(openid, emptyList()) } returns openidResource
        every { scopeMapper.toResource(profile, profileClaims) } returns profileResource

        val result = controller.listScopes(null, null, null, null)

        assertEquals(DEFAULT_PAGE, result.page)
        assertEquals(TEST_DEFAULT_PAGE_SIZE, result.size)
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

        val profileClaims = listOf(mockk<Claim>())
        val profileResource = mockResource("profile", "consentable", listOf("name"))

        coEvery { scopeManager.listAllScopes() } returns listOf(profile, openid)
        coEvery { scopeManager.listClaimsProtectedByScope(profile) } returns profileClaims
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

        coEvery { scopeManager.listAllScopes() } returns listOf(profile, openid)
        coEvery { scopeManager.listClaimsProtectedByScope(openid) } returns emptyList()
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

        coEvery { scopeManager.listAllScopes() } returns listOf(profile, usersRead)
        coEvery { scopeManager.listClaimsProtectedByScope(usersRead) } returns emptyList()
        every { scopeMapper.toResource(usersRead, emptyList()) } returns usersReadResource

        val result = controller.listScopes(null, null, "client", null)

        assertEquals(1, result.total)
        assertEquals(1, result.scopes.size)
        assertSame(usersReadResource, result.scopes[0])
    }

    @Test
    fun `listScopes - Unknown type returns empty list`() = runTest {
        val profile = ConsentableUserScope("profile")

        coEvery { scopeManager.listAllScopes() } returns listOf(profile)

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

        coEvery { scopeManager.listAllScopes() } returns scopes
        // Sorted by scope id, the second page of two holds openid and phone.
        listOf(2, 3).forEach { i ->
            coEvery { scopeManager.listClaimsProtectedByScope(scopes[i]) } returns emptyList()
            every { scopeMapper.toResource(scopes[i], emptyList()) } returns resources[i]
        }

        val result = controller.listScopes(1, 2, null, null)

        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(5, result.total)
        assertEquals(2, result.scopes.size)
        assertSame(resources[2], result.scopes[0])
        assertSame(resources[3], result.scopes[1])
    }

    @Test
    fun `listScopes - Order the whole list before slicing it`() = runTest {
        val address = ConsentableUserScope("address")
        val email = ConsentableUserScope("email")

        // Handed last-first, the first page still holds the two scopes the order puts first.
        coEvery { scopeManager.listAllScopes() } returns listOf(ConsentableUserScope("profile"), email, address)
        coEvery { scopeManager.listClaimsProtectedByScope(address) } returns emptyList()
        coEvery { scopeManager.listClaimsProtectedByScope(email) } returns emptyList()
        every { scopeMapper.toResource(address, emptyList()) } returns mockResource("address", "consentable")
        every { scopeMapper.toResource(email, emptyList()) } returns mockResource("email", "consentable")

        val result = controller.listScopes(0, 2, null, null)

        assertEquals(listOf("address", "email"), result.scopes.map { it.id })
    }

    @Test
    fun `listScopes - Return empty page when page exceeds total`() = runTest {
        val scope = ConsentableUserScope("profile")
        coEvery { scopeManager.listAllScopes() } returns listOf(scope)

        val result = controller.listScopes(5, 20, null, null)

        assertEquals(5, result.page)
        assertEquals(1, result.total)
        assertTrue(result.scopes.isEmpty())
    }

    @Test
    fun `listScopes - Claims populated for consentable, null for others`() = runTest {
        val profile = ConsentableUserScope("profile")
        val openid = GrantableUserScope("openid", discoverable = true)

        val profileClaims = listOf(mockk<Claim>())

        coEvery { scopeManager.listAllScopes() } returns listOf(profile, openid)
        coEvery { scopeManager.listClaimsProtectedByScope(profile) } returns profileClaims
        coEvery { scopeManager.listClaimsProtectedByScope(openid) } returns emptyList()
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

    @Test
    fun `listScopes - Return the scopes the deployment turned off alongside the served ones`() = runTest {
        val profile = ConsentableUserScope("profile")
        val phone = DisabledScope("phone", type = ScopeType.CONSENTABLE)

        val profileResource = mockResource("profile", "consentable")
        val phoneResource = mockResource("phone", "consentable", enabled = false)

        coEvery { scopeManager.listAllScopes() } returns listOf(profile, phone)
        coEvery { scopeManager.listClaimsProtectedByScope(profile) } returns emptyList()
        coEvery { scopeManager.listClaimsProtectedByScope(phone) } returns emptyList()
        every { scopeMapper.toResource(profile, emptyList()) } returns profileResource
        every { scopeMapper.toResource(phone, emptyList()) } returns phoneResource

        val result = controller.listScopes(null, null, null, null)

        assertEquals(2, result.total)
        assertEquals(listOf("phone", "profile"), result.scopes.map { it.id })
    }

    @Test
    fun `listScopes - Filter by enabled true`() = runTest {
        val profile = ConsentableUserScope("profile")
        val phone = DisabledScope("phone", type = ScopeType.CONSENTABLE)

        val profileResource = mockResource("profile", "consentable")

        coEvery { scopeManager.listAllScopes() } returns listOf(profile, phone)
        coEvery { scopeManager.listClaimsProtectedByScope(profile) } returns emptyList()
        every { scopeMapper.toResource(profile, emptyList()) } returns profileResource

        val result = controller.listScopes(null, null, null, true)

        assertEquals(1, result.total)
        assertSame(profileResource, result.scopes.single())
    }

    @Test
    fun `listScopes - Filter by enabled false`() = runTest {
        val profile = ConsentableUserScope("profile")
        val phone = DisabledScope("phone", type = ScopeType.CONSENTABLE)

        val phoneResource = mockResource("phone", "consentable", enabled = false)

        coEvery { scopeManager.listAllScopes() } returns listOf(profile, phone)
        coEvery { scopeManager.listClaimsProtectedByScope(phone) } returns emptyList()
        every { scopeMapper.toResource(phone, emptyList()) } returns phoneResource

        val result = controller.listScopes(null, null, null, false)

        assertEquals(1, result.total)
        assertSame(phoneResource, result.scopes.single())
    }

    @Test
    fun `listScopes - Filter by type finds a scope the deployment turned off`() = runTest {
        val phone = DisabledScope("phone", type = ScopeType.CONSENTABLE)
        val openid = GrantableUserScope("openid", discoverable = true)

        val phoneResource = mockResource("phone", "consentable", enabled = false)

        coEvery { scopeManager.listAllScopes() } returns listOf(phone, openid)
        coEvery { scopeManager.listClaimsProtectedByScope(phone) } returns emptyList()
        every { scopeMapper.toResource(phone, emptyList()) } returns phoneResource

        val result = controller.listScopes(null, null, "consentable", null)

        assertEquals(1, result.total)
        assertSame(phoneResource, result.scopes.single())
    }

    @Test
    fun `listScopes - Combine the type and enabled filters`() = runTest {
        val profile = ConsentableUserScope("profile")
        val phone = DisabledScope("phone", type = ScopeType.CONSENTABLE)
        val disabledGrantable = DisabledScope("reports", type = ScopeType.GRANTABLE)

        val phoneResource = mockResource("phone", "consentable", enabled = false)

        coEvery { scopeManager.listAllScopes() } returns listOf(profile, phone, disabledGrantable)
        coEvery { scopeManager.listClaimsProtectedByScope(phone) } returns emptyList()
        every { scopeMapper.toResource(phone, emptyList()) } returns phoneResource

        val result = controller.listScopes(null, null, "consentable", false)

        assertEquals(1, result.total)
        assertSame(phoneResource, result.scopes.single())
    }
}

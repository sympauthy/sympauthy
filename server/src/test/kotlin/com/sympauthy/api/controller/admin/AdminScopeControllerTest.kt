package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminScopeResourceMapper
import com.sympauthy.api.resource.admin.AdminScopeResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.ScopeSearchManager
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.manager.ScopeSearchManager.ScopeWithClaims
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.claim.Claim
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminScopeControllerTest {

    @MockK
    lateinit var scopeSearchManager: ScopeSearchManager

    @MockK
    lateinit var scopeMapper: AdminScopeResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminScopeController

    private val defaultPage = PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)

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

    private fun pageOf(vararg scopes: ScopeWithClaims) = Page(
        items = scopes.toList(),
        page = DEFAULT_PAGE,
        size = TEST_DEFAULT_PAGE_SIZE,
        total = scopes.size
    )

    @Test
    fun `listScopes - Map every scope the page holds, in the order it holds them`() = runTest {
        val profile = ConsentableUserScope("profile")
        val openid = GrantableUserScope("openid", discoverable = true)

        val profileClaims = listOf(mockk<Claim>(), mockk<Claim>())
        val profileResource = mockResource("profile", "consentable", listOf("name", "family_name"))
        val openidResource = mockResource("openid", "grantable")

        coEvery { scopeSearchManager.listScopes(null, null, defaultPage) } returns pageOf(
            ScopeWithClaims(openid, emptyList()),
            ScopeWithClaims(profile, profileClaims)
        )
        every { scopeMapper.toResource(openid, emptyList()) } returns openidResource
        every { scopeMapper.toResource(profile, profileClaims) } returns profileResource

        val result = controller.listScopes(null, null, null, null)

        assertEquals(listOf(openidResource, profileResource), result.scopes)
    }

    @Test
    fun `listScopes - Ask the manager for the scopes the parameters name, on the page they name`() = runTest {
        val profile = ConsentableUserScope("profile")
        val profileResource = mockResource("profile", "consentable")

        coEvery {
            scopeSearchManager.listScopes(ScopeType.CONSENTABLE, true, PageParams(1, 2))
        } returns pageOf(ScopeWithClaims(profile, emptyList()))
        every { scopeMapper.toResource(profile, emptyList()) } returns profileResource

        val result = controller.listScopes(1, 2, "consentable", true)

        assertSame(profileResource, result.scopes.single())
    }

    @Test
    fun `listScopes - Refuse a type the set does not hold`() = runTest {
        // The search is left unstubbed on purpose: reaching the assertion is proof it was never asked.
        val exception = assertThrows<LocalizedHttpException> {
            controller.listScopes(null, null, "consentible", null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listScopes - Publish the page the manager answered, not the one that was asked for`() = runTest {
        coEvery { scopeSearchManager.listScopes(null, null, defaultPage) } returns Page(
            items = emptyList(),
            page = 3,
            size = 7,
            total = 42
        )

        val result = controller.listScopes(null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}

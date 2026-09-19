package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminScopeResourceMapper
import com.sympauthy.api.resource.admin.AdminScopeResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.business.manager.collection.ScopeCollectionManager
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.manager.collection.ScopeCollectionManager.ScopeWithClaims
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldType
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.enumFieldValues
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
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminScopeControllerTest {

    @MockK
    lateinit var scopeCollectionManager: ScopeCollectionManager

    @MockK
    lateinit var scopeMapper: AdminScopeResourceMapper

    @MockK
    lateinit var capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminScopeController

    private val defaultPage = PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)

    private val typeField = CollectionField(
        name = "type",
        type = CollectionFieldType.ENUM,
        key = "fields.scope_type",
        operators = setOf(CollectionOperator.EQ),
        values = enumFieldValues<ScopeType>("fields.scope_type")
    )

    private val capabilities = CollectionCapabilities(listOf(typeField), emptyList())

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

        coEvery { scopeCollectionManager.capabilities() } returns capabilities
        coEvery { scopeCollectionManager.listScopes(any(), defaultPage) } returns pageOf(
            ScopeWithClaims(openid, emptyList()),
            ScopeWithClaims(profile, profileClaims)
        )
        every { scopeMapper.toResource(openid, emptyList()) } returns openidResource
        every { scopeMapper.toResource(profile, profileClaims) } returns profileResource

        val result = controller.listScopes(collectionRequest(), null, null, null, null)

        assertEquals(listOf(openidResource, profileResource), result.scopes)
    }

    @Test
    fun `listScopes - Hand the manager the criteria the request carries, on the page it names`() = runTest {
        val profile = ConsentableUserScope("profile")
        val profileResource = mockResource("profile", "consentable")
        val criteria = slot<CollectionCriteria>()

        coEvery { scopeCollectionManager.capabilities() } returns capabilities
        coEvery {
            scopeCollectionManager.listScopes(capture(criteria), PageParams(1, 2))
        } returns pageOf(ScopeWithClaims(profile, emptyList()))
        every { scopeMapper.toResource(profile, emptyList()) } returns profileResource

        val result = controller.listScopes(collectionRequest("type=consentable"), 1, 2, null, null)

        assertSame(profileResource, result.scopes.single())
        assertEquals(listOf("consentable"), criteria.captured.filters.single().values)
    }

    @Test
    fun `listScopes - Refuse a type the set does not hold`() = runTest {
        coEvery { scopeCollectionManager.capabilities() } returns capabilities

        // The manager is left unstubbed on purpose: reaching the assertion is proof it was never asked.
        val exception = assertThrows<LocalizedHttpException> {
            controller.listScopes(collectionRequest("type=consentible"), null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listScopes - Publish the page the manager answered, not the one that was asked for`() = runTest {
        coEvery { scopeCollectionManager.capabilities() } returns capabilities
        coEvery { scopeCollectionManager.listScopes(any(), defaultPage) } returns Page(
            items = emptyList(),
            page = 3,
            size = 7,
            total = 42
        )

        val result = controller.listScopes(collectionRequest(), null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}

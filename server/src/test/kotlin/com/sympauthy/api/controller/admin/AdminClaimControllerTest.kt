package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminClaimResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.ClaimSearchManager
import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.claim.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminClaimControllerTest {

    @MockK
    lateinit var claimSearchManager: ClaimSearchManager

    @MockK
    lateinit var claimMapper: AdminClaimResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminClaimController

    private val defaultPage = PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)

    private fun pageOf(vararg claims: Claim) = Page(
        items = claims.toList(),
        page = DEFAULT_PAGE,
        size = TEST_DEFAULT_PAGE_SIZE,
        total = claims.size
    )

    private val acl = ClaimAcl(
        consent = ConsentAcl(
            scope = null,
            readableByUser = false,
            writableByUser = false,
            readableByClient = false,
            writableByClient = false
        ),
        unconditional = UnconditionalAcl(emptyList(), emptyList())
    )

    private fun claim(id: String, enabled: Boolean) = Claim(
        id = id,
        enabled = enabled,
        verifiedId = null,
        dataType = ClaimDataType.STRING,
        group = null,
        required = false,
        generated = false,
        userInputted = false,
        allowedValues = null,
        acl = acl
    )

    private fun mockResource(claimId: String, enabled: Boolean) = AdminClaimResource(
        id = claimId,
        type = "string",
        origin = "custom",
        enabled = enabled,
        required = false,
        identifier = false,
        allowedValues = null,
        group = null
    )

    @Test
    fun `listClaims - Map every claim the page holds, in the order it holds them`() = runTest {
        val enabled = claim("a", enabled = true)
        val disabled = claim("_disabled", enabled = false)

        coEvery { claimSearchManager.listClaims(null, null, ValueFilter.Unfiltered, defaultPage) } returns
                pageOf(enabled, disabled)
        listOf(enabled, disabled).forEach {
            every { claimMapper.toResource(it) } returns mockResource(it.id, it.enabled)
        }

        val result = controller.listClaims(null, null, null, null, null)

        assertEquals(listOf("a", "_disabled"), result.claims.map { it.id })
    }

    @Test
    fun `listClaims - Ask the manager for the claims the parameters name, on the page they name`() = runTest {
        val email = claim(OpenIdConnectClaimId.EMAIL, enabled = true)
        val resource = mockResource(email.id, email.enabled)

        coEvery {
            claimSearchManager.listClaims(
                true, false, ValueFilter.Matching(ClaimOrigin.OPENID_CONNECT), PageParams(1, 2)
            )
        } returns pageOf(email)
        every { claimMapper.toResource(email) } returns resource

        val result = controller.listClaims(1, 2, true, false, "openid")

        assertSame(resource, result.claims.single())
    }

    @Test
    fun `listClaims - Ask the manager for nothing when the origin names no origin`() = runTest {
        coEvery {
            claimSearchManager.listClaims(null, null, ValueFilter.MatchesNothing, defaultPage)
        } returns pageOf()

        val result = controller.listClaims(null, null, null, null, "openid_connect")

        assertEquals(0, result.total)
        assertTrue(result.claims.isEmpty())
    }

    @Test
    fun `listClaims - Publish the page the manager answered, not the one that was asked for`() = runTest {
        coEvery { claimSearchManager.listClaims(null, null, ValueFilter.Unfiltered, defaultPage) } returns Page(
            items = emptyList(),
            page = 3,
            size = 7,
            total = 42
        )

        val result = controller.listClaims(null, null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}

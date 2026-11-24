package com.sympauthy.api.operator.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.operator.admin.mapper.AdminClaimResourceMapper
import com.sympauthy.api.operator.admin.mapper.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.operator.admin.resource.AdminClaimResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.claim.model.Claim
import com.sympauthy.business.claim.model.ClaimAcl
import com.sympauthy.business.claim.model.ClaimDataType
import com.sympauthy.business.claim.model.ClaimKind
import com.sympauthy.business.claim.model.ClaimOrigin
import com.sympauthy.business.claim.model.ClaimPublicationPlace
import com.sympauthy.business.claim.model.DisabledClaim
import com.sympauthy.business.claim.model.OpenIdConnectClaimId
import com.sympauthy.business.claim.model.PersonalClaim
import com.sympauthy.business.claim.model.isEnabled
import com.sympauthy.business.collection.manager.ClaimCollectionManager
import com.sympauthy.business.collection.model.CollectionCapabilities
import com.sympauthy.business.collection.model.CollectionCriteria
import com.sympauthy.business.collection.model.CollectionField
import com.sympauthy.business.collection.model.CollectionFieldType
import com.sympauthy.business.collection.model.CollectionOperator
import com.sympauthy.business.collection.model.Page
import com.sympauthy.business.collection.model.PageParams
import com.sympauthy.business.collection.model.enumFieldValues
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminClaimControllerTest {

    @MockK
    lateinit var claimCollectionManager: ClaimCollectionManager

    @MockK
    lateinit var claimMapper: AdminClaimResourceMapper

    @MockK
    lateinit var capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminClaimController

    private val defaultPage = PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)

    private val originField = CollectionField(
        name = "origin",
        type = CollectionFieldType.ENUM,
        key = "fields.claim_origin",
        operators = setOf(CollectionOperator.EQ),
        values = enumFieldValues<ClaimOrigin>("fields.claim_origin")
    )

    private val capabilities = CollectionCapabilities(listOf(originField), emptyList())

    private fun pageOf(vararg claims: Claim) = Page(
        items = claims.toList(),
        page = DEFAULT_PAGE,
        size = TEST_DEFAULT_PAGE_SIZE,
        total = claims.size
    )

    private val acl = ClaimAcl(
        collectedInFlow = null,
        readableByPerson = null,
        writableByPerson = null,
        readableByClient = null,
        writableByClient = null
    )

    private fun claim(id: String, enabled: Boolean): Claim = if (enabled) {
        PersonalClaim(
            id = id,
            verifiedId = null,
            dataType = ClaimDataType.STRING,
            group = null,
            required = false,
            allowedValues = null,
            publishedIn = ClaimPublicationPlace.entries.toSet(),
            publishedInWhenRequested = emptySet(),
            acl = acl
        )
    } else {
        DisabledClaim(
            id = id,
            verifiedId = null,
            dataType = ClaimDataType.STRING,
            kind = ClaimKind.PERSONAL,
            group = null,
            required = false,
            allowedValues = null,
            acl = acl
        )
    }

    private fun mockResource(claimId: String, enabled: Boolean) = AdminClaimResource(
        id = claimId,
        type = "string",
        origin = "custom",
        kind = "personal",
        enabled = enabled,
        required = false,
        identifier = false,
        allowedValues = null,
        group = null,
        publishedIn = listOf("id_token", "userinfo"),
        publishedInWhenRequested = emptyList()
    )

    @Test
    fun `listClaims - Map every claim the page holds, in the order it holds them`() = runTest {
        val enabled = claim("a", enabled = true)
        val disabled = claim("_disabled", enabled = false)

        coEvery { claimCollectionManager.capabilities() } returns capabilities
        coEvery { claimCollectionManager.listClaims(any(), defaultPage) } returns pageOf(enabled, disabled)
        listOf(enabled, disabled).forEach {
            every { claimMapper.toResource(it) } returns mockResource(it.id, it.isEnabled)
        }

        val result = controller.listClaims(collectionRequest(), null, null, null, null)

        assertEquals(listOf("a", "_disabled"), result.claims.map { it.id })
    }

    @Test
    fun `listClaims - Hand the manager the criteria the request carries, on the page it names`() = runTest {
        val email = claim(OpenIdConnectClaimId.EMAIL, enabled = true)
        val resource = mockResource(email.id, email.isEnabled)
        val criteria = slot<CollectionCriteria>()

        coEvery { claimCollectionManager.capabilities() } returns capabilities
        coEvery { claimCollectionManager.listClaims(capture(criteria), PageParams(1, 2)) } returns pageOf(email)
        every { claimMapper.toResource(email) } returns resource

        val result = controller.listClaims(collectionRequest("origin=openid"), 1, 2, null, null)

        assertSame(resource, result.claims.single())
        val filter = criteria.captured.filters.single()
        assertEquals("origin", filter.field.name)
        assertEquals(CollectionOperator.EQ, filter.operator)
        assertEquals(listOf("openid"), filter.values)
    }

    @Test
    fun `listClaims - Refuse an origin the set does not hold`() = runTest {
        coEvery { claimCollectionManager.capabilities() } returns capabilities

        // The manager is left unstubbed on purpose: reaching the assertion is proof it was never asked.
        val exception = assertThrows<LocalizedHttpException> {
            controller.listClaims(collectionRequest("origin=openid_connect"), null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listClaims - Refuse a parameter naming no field this collection filters on`() = runTest {
        coEvery { claimCollectionManager.capabilities() } returns capabilities

        val exception = assertThrows<LocalizedHttpException> {
            controller.listClaims(collectionRequest("enabled=true"), null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.unknown_field", exception.detailsId)
    }

    @Test
    fun `listClaims - Publish the page the manager answered, not the one that was asked for`() = runTest {
        coEvery { claimCollectionManager.capabilities() } returns capabilities
        coEvery { claimCollectionManager.listClaims(any(), defaultPage) } returns Page(
            items = emptyList(),
            page = 3,
            size = 7,
            total = 42
        )

        val result = controller.listClaims(collectionRequest(), null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}

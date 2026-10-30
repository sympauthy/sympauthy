package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminGrantResourceMapper
import com.sympauthy.api.resource.admin.AdminGrantDetailResource
import com.sympauthy.api.resource.admin.AdminGrantSummaryResource
import com.sympauthy.api.resource.admin.AdminRevokeGrantsInputResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.collection.GrantCollectionManager
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldType
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.enumFieldValues
import com.sympauthy.business.model.grant.Grant
import com.sympauthy.business.model.grant.GrantRevocationCriteria
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.grant.GrantStatus
import com.sympauthy.business.model.grant.GrantWithStatus
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.security.UserAuthentication
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminGrantControllerTest {

    @MockK
    lateinit var grantManager: GrantManager

    @MockK
    lateinit var grantCollectionManager: GrantCollectionManager

    @MockK
    lateinit var grantMapper: AdminGrantResourceMapper

    @MockK
    lateinit var capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminGrantController

    private val defaultPage = PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)

    private val capabilities = CollectionCapabilities(
        fields = listOf(
            CollectionField(
                name = "status",
                type = CollectionFieldType.ENUM,
                key = "fields.grant_status",
                operators = setOf(CollectionOperator.EQ),
                values = enumFieldValues<GrantStatus>("fields.grant_status")
            )
        ),
        defaultSort = emptyList()
    )

    private val grantId: UUID = UUID.randomUUID()

    private val adminId: UUID = UUID.randomUUID()

    private val grant = mockk<Grant>()

    private fun authenticationOf(userId: UUID): UserAuthentication = mockk {
        every { authenticationToken } returns mockk<AuthenticationToken> {
            every { grant } returns mockk { every { this@mockk.userId } returns userId }
        }
    }

    @Test
    fun `listGrants - Hand the manager the criteria the request carries, on the page it names`() = runTest {
        val live = GrantWithStatus(grant, GrantStatus.LIVE)
        val resource = mockk<AdminGrantSummaryResource>()
        val criteria = slot<CollectionCriteria>()
        coEvery { grantCollectionManager.capabilities() } returns capabilities
        coEvery { grantCollectionManager.listGrants(capture(criteria), PageParams(1, 2)) } returns
                Page(items = listOf(live), page = 1, size = 2, total = 3)
        every { grantMapper.toSummaryResource(live) } returns resource

        val result = controller.listGrants(collectionRequest("status=live"), 1, 2, null, null)

        assertSame(resource, result.grants.single())
        assertEquals(listOf("live"), criteria.captured.filters.single().values)
        assertEquals(3, result.total)
    }

    @Test
    fun `listGrants - Refuse a status the set does not hold`() = runTest {
        coEvery { grantCollectionManager.capabilities() } returns capabilities

        // The manager is left unstubbed on purpose: reaching the assertion is proof it was never asked.
        val exception = assertThrows<LocalizedHttpException> {
            controller.listGrants(collectionRequest("status=active"), null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listGrants - Publish the page the manager answered, not the one that was asked for`() = runTest {
        coEvery { grantCollectionManager.capabilities() } returns capabilities
        coEvery { grantCollectionManager.listGrants(any(), defaultPage) } returns
                Page(items = emptyList(), page = 3, size = 7, total = 42)

        val result = controller.listGrants(collectionRequest(), null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }

    @Test
    fun `getGrant - Answer the detail of the grant the identifier names`() = runTest {
        val live = GrantWithStatus(grant, GrantStatus.LIVE)
        val resource = mockk<AdminGrantDetailResource>()
        coEvery { grantCollectionManager.findGrantOrNull(grantId) } returns live
        every { grantMapper.toDetailResource(live) } returns resource

        assertSame(resource, controller.getGrant(grantId))
    }

    @Test
    fun `getGrant - Answer not_found for an identifier no grant has`() = runTest {
        coEvery { grantCollectionManager.findGrantOrNull(grantId) } returns null

        val exception = assertThrows<LocalizedHttpException> { controller.getGrant(grantId) }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals("not_found", exception.detailsId)
    }

    @Test
    fun `revokeGrants - Revoke the grants the body names as the administrator calling`() = runTest {
        val userId = UUID.randomUUID()
        val criteria = GrantRevocationCriteria(userIds = setOf(userId), clientIds = setOf("web"))
        coEvery { grantManager.revokeGrants(criteria, GrantRevokedBy.ADMIN, adminId) } returns 2

        val result = controller.revokeGrants(
            AdminRevokeGrantsInputResource(
                grantIds = null,
                userIds = listOf(userId),
                clientIds = listOf("web"),
                audienceIds = null
            ),
            authenticationOf(adminId)
        )

        assertEquals(2, result.grantsRevoked)
    }

    @Test
    fun `revokeGrants - Hand an empty list on as narrowing to no grant`() = runTest {
        val criteria = GrantRevocationCriteria(grantIds = emptySet())
        coEvery { grantManager.revokeGrants(criteria, GrantRevokedBy.ADMIN, adminId) } returns 0

        val result = controller.revokeGrants(
            AdminRevokeGrantsInputResource(
                grantIds = emptyList(),
                userIds = null,
                clientIds = null,
                audienceIds = null
            ),
            authenticationOf(adminId)
        )

        assertEquals(0, result.grantsRevoked)
    }

    @Test
    fun `revokeGrants - Refuse a body sending no set rather than revoke every grant`() = runTest {
        // The manager is left unstubbed on purpose: reaching the assertion is proof it was never asked.
        val exception = assertThrows<LocalizedHttpException> {
            controller.revokeGrants(
                AdminRevokeGrantsInputResource(grantIds = null, userIds = null, clientIds = null, audienceIds = null),
                mockk<UserAuthentication>()
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("grant.revoke.no_criterion", exception.detailsId)
    }
}

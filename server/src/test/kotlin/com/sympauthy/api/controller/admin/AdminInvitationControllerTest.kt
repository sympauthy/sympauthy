package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminInvitationResourceMapper
import com.sympauthy.api.resource.admin.AdminInvitationResource
import com.sympauthy.api.util.DEFAULT_PAGE
import com.sympauthy.api.util.TEST_DEFAULT_PAGE_SIZE
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.collection.InvitationCollectionManager
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionField
import com.sympauthy.business.model.collection.CollectionFieldType
import com.sympauthy.business.model.collection.CollectionOperator
import com.sympauthy.business.model.collection.enumFieldValues
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import io.micronaut.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AdminInvitationControllerTest {

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var invitationCollectionManager: InvitationCollectionManager

    @MockK
    lateinit var invitationMapper: AdminInvitationResourceMapper

    @MockK
    lateinit var capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminInvitationController

    private val createdAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

    private val defaultPage = PageParams(DEFAULT_PAGE, TEST_DEFAULT_PAGE_SIZE)

    private val statusField = CollectionField(
        name = "status",
        type = CollectionFieldType.ENUM,
        key = "fields.invitation_status",
        operators = setOf(CollectionOperator.EQ),
        values = enumFieldValues<InvitationStatus>("fields.invitation_status")
    )

    private val capabilities = CollectionCapabilities(listOf(statusField), emptyList())

    private fun pageOf(vararg invitations: Invitation) = Page(
        items = invitations.toList(),
        page = DEFAULT_PAGE,
        size = TEST_DEFAULT_PAGE_SIZE,
        total = invitations.size
    )

    private fun id(last: Int): UUID = UUID.fromString("00000000-0000-0000-0000-00000000000$last")

    private fun invitation(id: UUID, createdAt: LocalDateTime) = Invitation(
        id = id,
        audienceId = "default",
        tokenPrefix = "abcdefgh",
        claims = null,
        note = null,
        status = InvitationStatus.PENDING,
        createdBy = InvitationCreatedBy.ADMIN,
        createdById = null,
        consumedByUserId = null,
        createdAt = createdAt,
        expiresAt = createdAt.plusDays(7),
        consumedAt = null,
        revokedAt = null
    )

    private fun mockResource(invitationId: UUID) = AdminInvitationResource(
        invitationId = invitationId,
        audienceId = "default",
        tokenPrefix = "abcdefgh",
        status = "pending",
        claims = null,
        note = null,
        createdBy = "admin",
        createdAt = createdAt,
        expiresAt = createdAt.plusDays(7),
        userId = null,
        consumedAt = null,
        revokedAt = null
    )

    @Test
    fun `listInvitations - Map every invitation the page holds, in the order it holds them`() = runTest {
        val first = invitation(id(1), createdAt)
        val second = invitation(id(2), createdAt)

        coEvery { invitationCollectionManager.capabilities() } returns capabilities
        coEvery { invitationCollectionManager.listInvitations(any(), defaultPage) } returns pageOf(first, second)
        listOf(first, second).forEach {
            every { invitationMapper.toResource(it) } returns mockResource(it.id)
        }

        val result = controller.listInvitations(collectionRequest(), null, null, null, null)

        assertEquals(listOf(first.id, second.id), result.invitations.map { it.invitationId })
    }

    @Test
    fun `listInvitations - Hand the manager the criteria the request carries, on the page it names`() =
        runTest {
            val revoked = invitation(id(1), createdAt)
            val resource = mockResource(revoked.id)
            val criteria = slot<CollectionCriteria>()

            coEvery { invitationCollectionManager.capabilities() } returns capabilities
            coEvery {
                invitationCollectionManager.listInvitations(capture(criteria), PageParams(1, 2))
            } returns pageOf(revoked)
            every { invitationMapper.toResource(revoked) } returns resource

            val result = controller.listInvitations(collectionRequest("status=revoked"), 1, 2, null, null)

            assertSame(resource, result.invitations.single())
            assertEquals(listOf("revoked"), criteria.captured.filters.single().values)
        }

    @Test
    fun `listInvitations - Refuse a status the set does not hold`() = runTest {
        coEvery { invitationCollectionManager.capabilities() } returns capabilities

        // The manager is left unstubbed on purpose: reaching the assertion is proof it was never asked.
        val exception = assertThrows<LocalizedHttpException> {
            controller.listInvitations(collectionRequest("status=cancelled"), null, null, null, null)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals("collection.filter.value.unsupported", exception.detailsId)
    }

    @Test
    fun `listInvitations - Publish the page the manager answered, not the one that was asked for`() = runTest {
        coEvery { invitationCollectionManager.capabilities() } returns capabilities
        coEvery { invitationCollectionManager.listInvitations(any(), defaultPage) } returns
                Page(items = emptyList(), page = 3, size = 7, total = 42)

        val result = controller.listInvitations(collectionRequest(), null, null, null, null)

        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}

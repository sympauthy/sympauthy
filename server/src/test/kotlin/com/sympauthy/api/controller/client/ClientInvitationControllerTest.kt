package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientInvitationResourceMapper
import com.sympauthy.api.resource.client.ClientInvitationResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.invitation.InvitationSearchManager
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.security.ClientAuthentication
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
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ClientInvitationControllerTest {

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var invitationSearchManager: InvitationSearchManager

    @MockK
    lateinit var invitationMapper: ClientInvitationResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: ClientInvitationController

    private val createdAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

    private fun clientAuthentication(clientId: String): ClientAuthentication {
        val authenticationToken = mockk<AuthenticationToken> {
            every { this@mockk.clientId } returns clientId
        }
        return ClientAuthentication(authenticationToken, emptyList())
    }

    private fun id(last: Int): UUID = UUID.fromString("00000000-0000-0000-0000-00000000000$last")

    private fun invitation(id: UUID, createdAt: LocalDateTime) = Invitation(
        id = id,
        audienceId = "default",
        tokenPrefix = "abcdefgh",
        claims = null,
        note = null,
        status = InvitationStatus.PENDING,
        createdBy = InvitationCreatedBy.CLIENT,
        createdById = "client",
        consumedByUserId = null,
        createdAt = createdAt,
        expiresAt = createdAt.plusDays(7),
        consumedAt = null,
        revokedAt = null
    )

    private fun mockResource(invitationId: UUID) = ClientInvitationResource(
        invitationId = invitationId,
        tokenPrefix = "abcdefgh",
        status = "pending",
        claims = null,
        note = null,
        createdAt = createdAt,
        expiresAt = createdAt.plusDays(7),
        userId = null,
        consumedAt = null
    )

    @Test
    fun `listInvitations - Map every invitation the page holds, and publish the page it came in`() = runTest {
        val invitation = invitation(id(1), createdAt)
        val resource = mockResource(invitation.id)

        coEvery {
            invitationSearchManager.listInvitationsCreatedBy("client", PageParams(0, 20))
        } returns Page(items = listOf(invitation), page = 3, size = 7, total = 42)
        every { invitationMapper.toResource(invitation) } returns resource

        val result = controller.listInvitations(clientAuthentication("client"), null, null)

        assertSame(resource, result.invitations.single())
        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}

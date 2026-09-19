package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientInvitationResourceMapper
import com.sympauthy.api.resource.client.ClientCreateInvitationInputResource
import com.sympauthy.api.resource.client.ClientCreatedInvitationResource
import com.sympauthy.api.resource.client.ClientInvitationResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.api.util.collectionRequest
import com.sympauthy.api.util.noCapabilities
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.collection.InvitationCollectionManager
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.ClientScope
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
    lateinit var invitationCollectionManager: InvitationCollectionManager

    @MockK
    lateinit var invitationMapper: ClientInvitationResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: ClientInvitationController

    private val createdAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

    private fun clientAuthentication(clientId: String, vararg scopes: String): ClientAuthentication {
        val authenticationToken = mockk<AuthenticationToken> {
            every { this@mockk.clientId } returns clientId
        }
        return ClientAuthentication(authenticationToken, scopes.map(::ClientScope))
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

    private fun createdResource(invitationId: UUID) = ClientCreatedInvitationResource(
        invitationId = invitationId,
        token = "raw-token",
        status = "pending",
        claims = null,
        note = null,
        createdAt = createdAt,
        expiresAt = createdAt.plusDays(7)
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

    /**
     * Nothing in the request names an audience, so the stub matching the client's own is the assertion: a
     * controller taking it from anywhere else reaches no stub at all. Refusing a claim that names another
     * audience is [com.sympauthy.business.manager.invitation.InvitationManager]'s, and is proved there.
     */
    @Test
    fun `createInvitation - Hold the pre-assigned claims to the authenticated client's own audience`() = runTest {
        val invitation = invitation(id(1), createdAt)
        val resource = createdResource(invitation.id)
        val claims = mapOf("custom_region" to "eu-west")

        coEvery { clientManager.findClientById("client") } returns mockk<Client> {
            every { id } returns "client"
            every { audience } returns Audience(id = "default", tokenAudience = "https://default.example.com")
        }
        coEvery {
            invitationManager.createInvitation(
                audienceId = "default",
                claims = claims,
                note = "welcome",
                expiresAt = null,
                createdBy = InvitationCreatedBy.CLIENT,
                createdById = "client",
                clientScopeIds = listOf("invitations:write", "users:claims:write"),
            )
        } returns (invitation to "raw-token")
        every { invitationMapper.toCreatedResource(invitation, "raw-token") } returns resource

        val result = controller.createInvitation(
            clientAuthentication("client", "invitations:write", "users:claims:write"),
            ClientCreateInvitationInputResource(expiresAt = null, claims = claims, note = "welcome"),
        )

        assertSame(resource, result)
    }

    @Test
    fun `listInvitations - Map every invitation the page holds, and publish the page it came in`() = runTest {
        val invitation = invitation(id(1), createdAt)
        val resource = mockResource(invitation.id)

        coEvery { invitationCollectionManager.clientCapabilities() } returns noCapabilities()
        coEvery {
            invitationCollectionManager.listInvitationsCreatedBy("client", any(), PageParams(0, 20))
        } returns Page(items = listOf(invitation), page = 3, size = 7, total = 42)
        every { invitationMapper.toResource(invitation) } returns resource

        val result = controller.listInvitations(
            collectionRequest(), clientAuthentication("client"), null, null, null, null
        )

        assertSame(resource, result.invitations.single())
        assertEquals(3, result.page)
        assertEquals(7, result.size)
        assertEquals(42, result.total)
    }
}

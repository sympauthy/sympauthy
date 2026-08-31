package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminInvitationResourceMapper
import com.sympauthy.api.resource.admin.AdminInvitationResource
import com.sympauthy.api.util.defaultPaginationUtil
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class AdminInvitationControllerTest {

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var invitationMapper: AdminInvitationResourceMapper

    @Suppress("unused")
    private val paginationUtil = defaultPaginationUtil()

    @InjectMockKs
    lateinit var controller: AdminInvitationController

    private val createdAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

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
    fun `listInvitations - Order by creation date, then by identifier`() = runTest {
        // Two of the three were created in the same instant, which is what the identifier separates.
        val tiedFirst = invitation(id(1), createdAt)
        val tiedSecond = invitation(id(2), createdAt)
        val earlier = invitation(id(3), createdAt.minusDays(1))

        coEvery { invitationManager.findAll() } returns listOf(tiedSecond, tiedFirst, earlier)
        listOf(tiedFirst, tiedSecond, earlier).forEach {
            every { invitationMapper.toResource(it) } returns mockResource(it.id)
        }

        val result = controller.listInvitations(null, null, null, null)

        assertEquals(
            listOf(earlier.id, tiedFirst.id, tiedSecond.id),
            result.invitations.map { it.invitationId }
        )
    }
}

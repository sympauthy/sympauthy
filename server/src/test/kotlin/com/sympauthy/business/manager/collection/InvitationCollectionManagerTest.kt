package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.api.util.criteriaOf
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.page.PageParams
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class InvitationCollectionManagerTest {

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var audienceManager: AudienceManager

    @InjectMockKs
    lateinit var invitationCollectionManager: InvitationCollectionManager

    private val createdAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)

    private fun invitation(
        id: UUID = UUID.randomUUID(),
        audienceId: String = "default",
        status: InvitationStatus = InvitationStatus.PENDING
    ) = Invitation(
        id = id,
        audienceId = audienceId,
        tokenPrefix = "abcdefgh",
        claims = null,
        note = null,
        status = status,
        createdBy = InvitationCreatedBy.ADMIN,
        createdById = null,
        consumedByUserId = null,
        createdAt = createdAt,
        expiresAt = createdAt.plusDays(7),
        consumedAt = null,
        revokedAt = null
    )

    private val pending = invitation(id = id(1))
    private val revoked = invitation(id = id(2), status = InvitationStatus.REVOKED)
    private val otherAudience = invitation(id = id(3), audienceId = "other")

    private val firstPage = PageParams(page = 0, size = 20)

    @Test
    fun `listInvitations - Keep every invitation when the criteria name nothing`() = runTest {
        coEvery { invitationManager.findAll() } returns listOf(pending, otherAudience)

        val result = invitationCollectionManager.listInvitations(criteriaOf(), firstPage)

        assertEquals(listOf(pending, otherAudience), result.items)
    }

    @Test
    fun `listInvitations - Keep the invitations of the audience the criterion names`() = runTest {
        coEvery { invitationManager.findAll() } returns listOf(pending, otherAudience)

        val result = invitationCollectionManager.listInvitations(criteriaOf("audience_id" to "other"), firstPage)

        assertEquals(listOf(otherAudience), result.items)
    }

    @Test
    fun `listInvitations - Keep the invitations of the status the criterion names`() = runTest {
        coEvery { invitationManager.findAll() } returns listOf(pending, revoked)

        val result = invitationCollectionManager.listInvitations(criteriaOf("status" to "revoked"), firstPage)

        assertEquals(listOf(revoked), result.items)
    }

    @Test
    fun `listInvitations - Keep the invitations both criteria name`() = runTest {
        val revokedInOtherAudience = invitation(id = id(4), audienceId = "other", status = InvitationStatus.REVOKED)
        coEvery { invitationManager.findAll() } returns listOf(otherAudience, revokedInOtherAudience)

        val criteria = criteriaOf("audience_id" to "other", "status" to "revoked")
        val result = invitationCollectionManager.listInvitations(criteria, firstPage)

        assertEquals(listOf(revokedInOtherAudience), result.items)
    }

    @Test
    fun `listInvitations - Keep the invitations of one of the statuses a list names`() = runTest {
        coEvery { invitationManager.findAll() } returns listOf(pending, revoked, otherAudience)

        val criteria = criteriaOf("status.in" to "revoked,consumed")
        val result = invitationCollectionManager.listInvitations(criteria, firstPage)

        assertEquals(listOf(revoked), result.items)
    }

    @Test
    fun `listInvitations - Keep the invitations created since the date a criterion names`() = runTest {
        val earlier = invitation(id = id(4)).copy(createdAt = createdAt.minusDays(2))
        coEvery { invitationManager.findAll() } returns listOf(pending, earlier)

        val criteria = criteriaOf("created_at.gte" to createdAt.toString())
        val result = invitationCollectionManager.listInvitations(criteria, firstPage)

        assertEquals(listOf(pending), result.items)
    }

    @Test
    fun `listInvitations - Order by creation date, then by identifier`() = runTest {
        // Two of the three were created in the same instant, which is what the identifier separates.
        val tied = invitation(id = id(1))
        val tiedLater = invitation(id = id(2))
        val earlier = invitation(id = id(3)).copy(createdAt = createdAt.minusDays(1))
        coEvery { invitationManager.findAll() } returns listOf(tiedLater, tied, earlier)

        val result = invitationCollectionManager.listInvitations(criteriaOf(), firstPage)

        assertEquals(listOf(earlier.id, tied.id, tiedLater.id), result.items.map { it.id })
    }

    @Test
    fun `listInvitations - Return the page the parameters name, out of everything the criteria kept`() = runTest {
        coEvery { invitationManager.findAll() } returns listOf(pending, revoked, otherAudience)

        val result = invitationCollectionManager.listInvitations(criteriaOf(), PageParams(1, 2))

        assertEquals(1, result.items.size)
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(3, result.total)
    }

    private fun id(last: Int): UUID = UUID.fromString("00000000-0000-0000-0000-00000000000$last")

    @Test
    fun `listInvitationsCreatedBy - Read the invitations that client created, oldest first`() = runTest {
        val tied = invitation(id = id(1))
        val tiedLater = invitation(id = id(2))
        val earlier = invitation(id = id(3)).copy(createdAt = createdAt.minusDays(1))
        coEvery { invitationManager.findByCreatedById("client") } returns listOf(tiedLater, tied, earlier)

        val result = invitationCollectionManager.listInvitationsCreatedBy(
            "client", invitationCollectionManager.clientCapabilities().criteriaOf(), firstPage
        )

        assertEquals(listOf(earlier.id, tied.id, tiedLater.id), result.items.map { it.id })
    }

    private suspend fun criteriaOf(
        vararg filters: Pair<String, String>,
        sort: String? = null,
        query: String? = null
    ): CollectionCriteria {
        every { audienceManager.listAudiences() } returns listOf(
            Audience(id = "default", tokenAudience = "default"),
            Audience(id = "other", tokenAudience = "other")
        )
        return invitationCollectionManager.capabilities().criteriaOf(*filters, sort = sort, query = query)
    }
}

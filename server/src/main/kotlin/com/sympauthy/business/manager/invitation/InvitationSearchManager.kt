package com.sympauthy.business.manager.invitation

import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.orderedPage
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager responsible for filtering the invitations this authorization server issued.
 *
 * Which invitations are read is a query, and which of them are kept is a filter over what came back.
 * The status the table stores is not the status an invitation has — a pending one past its expiry
 * date reads as expired — so it is the mapped invitation that answers the criterion.
 */
@Singleton
class InvitationSearchManager(
    @Inject private val invitationManager: InvitationManager
) {

    private val oldestFirst = compareBy<Invitation> { it.createdAt }.thenBy { it.id }

    /**
     * Read the page [pageParams] names of every [Invitation] the criteria keep, oldest first and
     * ties broken by identifier.
     *
     * [audienceId] and [status] compose, and each keeps every invitation where the caller named no
     * criterion. A [status] naming no [InvitationStatus] keeps nothing rather than everything.
     */
    suspend fun listInvitations(
        audienceId: String?,
        status: ValueFilter<InvitationStatus>,
        pageParams: PageParams
    ): Page<Invitation> {
        val invitations = if (audienceId != null) {
            invitationManager.findByAudienceId(audienceId)
        } else {
            invitationManager.findAll()
        }
        return invitations
            .filter { status.matches(it.status) }
            .orderedPage(pageParams, oldestFirst)
    }

    /**
     * Read the page [pageParams] names of the invitations [createdById] created, oldest first and
     * ties broken by identifier.
     */
    suspend fun listInvitationsCreatedBy(
        createdById: String,
        pageParams: PageParams
    ): Page<Invitation> {
        return invitationManager.findByCreatedById(createdById)
            .orderedPage(pageParams, oldestFirst)
    }
}

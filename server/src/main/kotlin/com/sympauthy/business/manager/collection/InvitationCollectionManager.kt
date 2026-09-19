package com.sympauthy.business.manager.collection

import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.collection.CollectionCapabilities
import com.sympauthy.business.model.collection.CollectionCriteria
import com.sympauthy.business.model.collection.CollectionFieldType.DATE_TIME
import com.sympauthy.business.model.collection.CollectionFieldType.ENUM
import com.sympauthy.business.model.collection.CollectionFieldType.STRING
import com.sympauthy.business.model.collection.CollectionFieldType.UUID
import com.sympauthy.business.model.collection.CollectionFields
import com.sympauthy.business.model.collection.CollectionFieldsBuilder
import com.sympauthy.business.model.collection.collectionFields
import com.sympauthy.business.model.collection.fieldValues
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager responsible for filtering the invitations this authorization server issued.
 *
 * The rows are read whole and the criteria are a filter over what came back, as every other admin
 * collection does — see [UserCollectionManager] for the reason. The status the table stores is not the
 * status an invitation has, a pending one past its expiry date reading as expired, so it is the
 * mapped invitation that answers a criterion.
 *
 * **The two collections are two field sets over one concept.** An administrator sees who created an
 * invitation and which audience it is for; a client sees its own invitations, all of them created by
 * it and all of them for its own audience, so neither field says anything there.
 */
@Singleton
class InvitationCollectionManager(
    @Inject private val invitationManager: InvitationManager,
    @Inject private val audienceManager: AudienceManager
) {

    /**
     * What the administration collection accepts, which is what it publishes about itself.
     */
    suspend fun capabilities(): CollectionCapabilities = adminFields().capabilities

    /**
     * What the client collection accepts.
     *
     * Nothing publishes it: what reads that surface is generated from the published specification, and
     * the values it would enumerate belong to the one client asking.
     */
    suspend fun clientCapabilities(): CollectionCapabilities = clientFields().capabilities

    /**
     * Read the page [pageParams] names of every [Invitation] [criteria] keep.
     */
    suspend fun listInvitations(
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<Invitation> = adminFields().page(invitationManager.findAll(), criteria, pageParams)

    /**
     * Read the page [pageParams] names of the invitations [createdById] created that [criteria]
     * keep.
     *
     * Who created them is not a criterion: it is the whole of what this caller may read.
     */
    suspend fun listInvitationsCreatedBy(
        createdById: String,
        criteria: CollectionCriteria,
        pageParams: PageParams
    ): Page<Invitation> = clientFields()
        .page(invitationManager.findByCreatedById(createdById), criteria, pageParams)

    private suspend fun adminFields(): CollectionFields<Invitation> = collectionFields(
        uniqueKey = compareBy(Invitation::id)
    ) {
        common()
        field(
            name = "audience_id",
            type = ENUM,
            key = "fields.audience_id",
            values = fieldValues("fields.audience_id", audienceManager.listAudiences().map(Audience::id)),
            sortable = true,
            read = Invitation::audienceId
        )
        enumeration<InvitationCreatedBy>(
            name = "created_by",
            key = "fields.invitation_created_by",
            sortable = true,
            read = Invitation::createdBy
        )
        field("created_by_id", STRING, key = "fields.client_id", nullable = true, read = Invitation::createdById)
        defaultSort("created_at")
    }

    private suspend fun clientFields(): CollectionFields<Invitation> = collectionFields(
        uniqueKey = compareBy(Invitation::id)
    ) {
        common()
        defaultSort("created_at")
    }

    /**
     * The fields both collections offer, which is everything an invitation carries that neither the
     * caller's own identity nor its audience already answers.
     */
    private fun CollectionFieldsBuilder<Invitation>.common() {
        field("id", UUID, key = "fields.invitation_id", read = Invitation::id)
        field("token_prefix", STRING, searchable = true, read = Invitation::tokenPrefix)
        enumeration<InvitationStatus>(
            name = "status",
            key = "fields.invitation_status",
            sortable = true,
            read = Invitation::status
        )
        field("note", STRING, nullable = true, searchable = true, read = Invitation::note)
        field("created_at", DATE_TIME, sortable = true, read = Invitation::createdAt)
        field("expires_at", DATE_TIME, sortable = true, read = Invitation::expiresAt)
        field("consumed_at", DATE_TIME, nullable = true, sortable = true, read = Invitation::consumedAt)
        field("revoked_at", DATE_TIME, nullable = true, sortable = true, read = Invitation::revokedAt)
        field("consumed_by_user_id", UUID, key = "fields.user_id", nullable = true, read = Invitation::consumedByUserId)
    }
}

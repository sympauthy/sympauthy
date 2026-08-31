package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminCreatedInvitationResource
import com.sympauthy.api.resource.admin.AdminInvitationResource
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class AdminInvitationResourceMapper {

    fun toResource(invitation: Invitation): AdminInvitationResource {
        return AdminInvitationResource(
            invitationId = invitation.id,
            audienceId = invitation.audienceId,
            tokenPrefix = invitation.tokenPrefix,
            status = invitation.status.wireName,
            claims = invitation.claims,
            note = invitation.note,
            createdBy = invitation.createdBy.wireName,
            createdAt = invitation.createdAt,
            expiresAt = invitation.expiresAt,
            userId = invitation.consumedByUserId,
            consumedAt = invitation.consumedAt,
            revokedAt = invitation.revokedAt,
        )
    }

    fun toCreatedResource(invitation: Invitation, rawToken: String): AdminCreatedInvitationResource {
        return AdminCreatedInvitationResource(
            invitationId = invitation.id,
            token = rawToken,
            audienceId = invitation.audienceId,
            status = invitation.status.wireName,
            claims = invitation.claims,
            note = invitation.note,
            createdAt = invitation.createdAt,
            expiresAt = invitation.expiresAt,
        )
    }
}

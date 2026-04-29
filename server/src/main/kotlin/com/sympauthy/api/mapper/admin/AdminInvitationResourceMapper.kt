package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminCreatedInvitationResource
import com.sympauthy.api.resource.admin.AdminInvitationResource
import com.sympauthy.business.model.invitation.Invitation
import jakarta.inject.Singleton

@Singleton
class AdminInvitationResourceMapper {

    fun toResource(invitation: Invitation): AdminInvitationResource {
        return AdminInvitationResource(
            invitationId = invitation.id,
            audience = invitation.audienceId,
            tokenPrefix = invitation.tokenPrefix,
            status = invitation.status.name.lowercase(),
            claims = invitation.claims,
            note = invitation.note,
            createdBy = invitation.createdBy.name.lowercase(),
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
            audience = invitation.audienceId,
            status = invitation.status.name.lowercase(),
            claims = invitation.claims,
            note = invitation.note,
            createdAt = invitation.createdAt,
            expiresAt = invitation.expiresAt,
        )
    }
}

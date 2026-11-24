package com.sympauthy.api.operator.admin.mapper

import com.sympauthy.api.operator.admin.resource.AdminCreatedInvitationResource
import com.sympauthy.api.operator.admin.resource.AdminInvitationResource
import com.sympauthy.business.invitation.model.Invitation
import com.sympauthy.platform.util.wireName
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

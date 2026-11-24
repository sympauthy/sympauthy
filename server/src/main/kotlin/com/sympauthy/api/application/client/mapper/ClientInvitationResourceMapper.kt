package com.sympauthy.api.application.client.mapper

import com.sympauthy.api.application.client.resource.ClientCreatedInvitationResource
import com.sympauthy.api.application.client.resource.ClientInvitationResource
import com.sympauthy.business.invitation.model.Invitation
import com.sympauthy.platform.util.wireName
import jakarta.inject.Singleton

@Singleton
class ClientInvitationResourceMapper {

    fun toResource(invitation: Invitation): ClientInvitationResource {
        return ClientInvitationResource(
            invitationId = invitation.id,
            tokenPrefix = invitation.tokenPrefix,
            status = invitation.status.wireName,
            claims = invitation.claims,
            note = invitation.note,
            createdAt = invitation.createdAt,
            expiresAt = invitation.expiresAt,
            userId = invitation.consumedByUserId,
            consumedAt = invitation.consumedAt,
        )
    }

    fun toCreatedResource(invitation: Invitation, rawToken: String): ClientCreatedInvitationResource {
        return ClientCreatedInvitationResource(
            invitationId = invitation.id,
            token = rawToken,
            status = invitation.status.wireName,
            claims = invitation.claims,
            note = invitation.note,
            createdAt = invitation.createdAt,
            expiresAt = invitation.expiresAt,
        )
    }
}

package com.sympauthy.api.mapper.client

import com.sympauthy.api.resource.client.ClientCreatedInvitationResource
import com.sympauthy.api.resource.client.ClientInvitationResource
import com.sympauthy.business.model.invitation.Invitation
import jakarta.inject.Singleton

@Singleton
class ClientInvitationResourceMapper {

    fun toResource(invitation: Invitation): ClientInvitationResource {
        return ClientInvitationResource(
            invitationId = invitation.id,
            tokenPrefix = invitation.tokenPrefix,
            status = invitation.status.name.lowercase(),
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
            status = invitation.status.name.lowercase(),
            claims = invitation.claims,
            note = invitation.note,
            createdAt = invitation.createdAt,
            expiresAt = invitation.expiresAt,
        )
    }
}

package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminAudienceResource
import com.sympauthy.business.model.audience.Audience
import jakarta.inject.Singleton

@Singleton
class AdminAudienceResourceMapper {

    fun toResource(audience: Audience, clientsCount: Int): AdminAudienceResource {
        return AdminAudienceResource(
            audienceId = audience.id,
            tokenAudience = audience.tokenAudience,
            signUpEnabled = audience.signUpEnabled,
            invitationEnabled = audience.invitationEnabled,
            clientsCount = clientsCount,
        )
    }
}

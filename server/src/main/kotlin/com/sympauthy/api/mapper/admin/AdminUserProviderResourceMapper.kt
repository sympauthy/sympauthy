package com.sympauthy.api.mapper.admin

import com.sympauthy.api.resource.admin.AdminUserProviderResource
import com.sympauthy.business.model.provider.ProviderUserInfo
import jakarta.inject.Singleton

@Singleton
class AdminUserProviderResourceMapper {

    fun toResource(providerUserInfo: ProviderUserInfo): AdminUserProviderResource {
        return AdminUserProviderResource(
            providerId = providerUserInfo.providerId,
            subject = providerUserInfo.userInfo.subject,
            linkedAt = providerUserInfo.linkDate,
        )
    }
}

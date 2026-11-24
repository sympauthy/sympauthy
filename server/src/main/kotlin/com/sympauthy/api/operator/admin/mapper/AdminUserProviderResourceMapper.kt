package com.sympauthy.api.operator.admin.mapper

import com.sympauthy.api.operator.admin.resource.AdminUserProviderResource
import com.sympauthy.business.provider.model.ProviderUserInfo
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

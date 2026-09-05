package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminUserProviderResource
import com.sympauthy.business.model.provider.ProviderUserInfo
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class AdminUserProviderResourceMapper {

    @Mapping(source = "userInfo.subject", target = "subject")
    @Mapping(source = "linkDate", target = "linkedAt")
    abstract fun toResource(providerUserInfo: ProviderUserInfo): AdminUserProviderResource
}

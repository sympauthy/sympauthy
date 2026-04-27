package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminAudienceResource
import com.sympauthy.business.model.audience.Audience
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class AdminAudienceResourceMapper {

    @Mapping(source = "id", target = "audienceId")
    abstract fun toResource(audience: Audience): AdminAudienceResource
}

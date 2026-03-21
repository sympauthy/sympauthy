package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminUserMfaMethodResource
import com.sympauthy.business.model.mfa.TotpEnrollment
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class AdminUserMfaMethodResourceMapper {

    @Mapping(source = "id", target = "mfaId")
    @Mapping(source = "confirmedDate", target = "registeredAt")
    @Mapping(target = "type", constant = "totp")
    abstract fun toResource(enrollment: TotpEnrollment): AdminUserMfaMethodResource
}

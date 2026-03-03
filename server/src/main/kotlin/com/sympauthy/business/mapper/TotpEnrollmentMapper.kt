package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.data.model.TotpEnrollmentEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = ToBusinessMapperConfig::class
)
interface TotpEnrollmentMapper {

    @Mapping(target = "id", source = "id")
    fun toTotpEnrollment(entity: TotpEnrollmentEntity): TotpEnrollment
}

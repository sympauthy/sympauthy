package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.securitycontext.SecurityContext
import com.sympauthy.business.model.securitycontext.SecurityContextGeo
import com.sympauthy.data.model.SecurityContextEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = ToBusinessMapperConfig::class
)
interface SecurityContextMapper {

    @Mapping(target = "geo", source = "entity")
    fun toSecurityContext(entity: SecurityContextEntity): SecurityContext

    fun toGeo(entity: SecurityContextEntity): SecurityContextGeo
}

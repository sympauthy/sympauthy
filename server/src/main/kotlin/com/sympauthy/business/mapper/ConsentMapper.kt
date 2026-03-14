package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.data.model.ConsentEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = ToBusinessMapperConfig::class
)
interface ConsentMapper {

    @Mapping(target = "id", source = "id")
    fun toConsent(entity: ConsentEntity): Consent

    fun toScopeList(scopes: Array<String>): List<String> = scopes.toList()
}

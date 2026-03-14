package com.sympauthy.api.mapper.admin

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.admin.AdminConsentResource
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class AdminConsentResourceMapper {

    @Mapping(source = "id", target = "consentId")
    abstract fun toResource(consent: Consent): AdminConsentResource

    fun toRevokedByString(value: ConsentRevokedBy?): String? = value?.name
}

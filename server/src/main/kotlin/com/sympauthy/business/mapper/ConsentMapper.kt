package com.sympauthy.business.mapper

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.oauth2.Consent
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.data.model.ConsentEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class ConsentMapper {

    @Mapping(target = "id", source = "id")
    abstract fun toConsent(entity: ConsentEntity): Consent

    fun toScopeList(scopes: Array<String>): List<String> = scopes.toList()

    fun toConsentRevokedBy(value: String?): ConsentRevokedBy? = value?.let {
        try {
            ConsentRevokedBy.valueOf(it)
        } catch (e: IllegalArgumentException) {
            throw businessExceptionOf(
                detailsId = "mapper.consent.invalid_property",
                values = arrayOf("property" to "revokedBy")
            )
        }
    }
}

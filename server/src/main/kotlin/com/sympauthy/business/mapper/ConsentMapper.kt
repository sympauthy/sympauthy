package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
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

    /**
     * The actor [value] names, or null where the consent is still active.
     *
     * A value [ConsentRevokedBy] does not name throws an internal [BusinessException]
     * "mapper.consent.invalid_property": a row this server wrote and cannot read back is its own
     * failure rather than the caller's.
     */
    fun toConsentRevokedBy(value: String?): ConsentRevokedBy? = value?.let {
        try {
            ConsentRevokedBy.valueOf(it)
        } catch (_: IllegalArgumentException) {
            throw internalBusinessExceptionOf(
                detailsId = "mapper.consent.invalid_property",
                values = arrayOf("property" to "revokedBy")
            )
        }
    }
}

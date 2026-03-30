package com.sympauthy.api.mapper

import com.sympauthy.api.mapper.config.OutputResourceMapperConfig
import com.sympauthy.api.resource.openid.AddressResource
import com.sympauthy.api.resource.openid.UserInfoResource
import com.sympauthy.business.model.user.RawProviderClaims
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Mappings
import java.time.LocalDateTime
import java.time.ZoneOffset

@Mapper(
    config = OutputResourceMapperConfig::class
)
abstract class UserInfoResourceMapper {

    @Mappings(
        Mapping(target = "sub", source = "subject"),
        Mapping(target = "address", expression = "java(toAddressResource(info))")
    )
    abstract fun toResource(info: RawProviderClaims): UserInfoResource

    fun toUpdatedAt(updatedAt: LocalDateTime?): Long? {
        return updatedAt?.toInstant(ZoneOffset.UTC)?.epochSecond
    }

    fun toAddressResource(info: RawProviderClaims): AddressResource? {
        if (info.streetAddress == null && info.locality == null && info.region == null
            && info.postalCode == null && info.country == null
        ) {
            return null
        }
        val formatted = listOfNotNull(
            info.streetAddress,
            listOfNotNull(info.locality, info.region, info.postalCode)
                .joinToString(", ").ifBlank { null },
            info.country
        ).joinToString("\n").ifBlank { null }
        return AddressResource(
            formatted = formatted,
            streetAddress = info.streetAddress,
            locality = info.locality,
            region = info.region,
            postalCode = info.postalCode,
            country = info.country
        )
    }
}

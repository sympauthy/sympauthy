package com.sympauthy.api.mapper.flow

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.mapstruct.factory.Mappers

@Factory
class FlowApiMapperFactory {

    @Singleton
    fun claimsResourceMapper(): ClaimsResourceMapper = Mappers.getMapper(ClaimsResourceMapper::class.java)

    @Singleton
    fun timeZoneResourceMapper(): TimeZoneResourceMapper = Mappers.getMapper(TimeZoneResourceMapper::class.java)

    @Singleton
    fun validationCodesResourceMapper(): ValidationCodesResourceMapper =
        Mappers.getMapper(ValidationCodesResourceMapper::class.java)
}

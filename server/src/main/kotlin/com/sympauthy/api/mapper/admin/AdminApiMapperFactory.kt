package com.sympauthy.api.mapper.admin

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.mapstruct.factory.Mappers

@Factory
class AdminApiMapperFactory {

    @Singleton
    fun clientResourceMapper(): AdminClientResourceMapper = Mappers.getMapper(AdminClientResourceMapper::class.java)

    @Singleton
    fun consentResourceMapper(): AdminConsentResourceMapper = Mappers.getMapper(AdminConsentResourceMapper::class.java)
}

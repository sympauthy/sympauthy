package com.sympauthy.api.mapper.client

import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.mapstruct.factory.Mappers

@Factory
class ClientApiMapperFactory {

    @Singleton
    fun clientUserResourceMapper(): ClientUserResourceMapper = Mappers.getMapper(ClientUserResourceMapper::class.java)
}

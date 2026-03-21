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

    @Singleton
    fun userDetailResourceMapper(): AdminUserDetailResourceMapper = Mappers.getMapper(AdminUserDetailResourceMapper::class.java)

    @Singleton
    fun userClaimResourceMapper(): AdminUserClaimResourceMapper = Mappers.getMapper(AdminUserClaimResourceMapper::class.java)

    @Singleton
    fun mfaMethodResourceMapper(): AdminUserMfaMethodResourceMapper = Mappers.getMapper(AdminUserMfaMethodResourceMapper::class.java)
}

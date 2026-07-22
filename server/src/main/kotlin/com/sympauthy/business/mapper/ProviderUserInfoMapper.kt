package com.sympauthy.business.mapper

import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.data.model.ProviderUserInfoEntity
import com.sympauthy.data.model.ProviderUserInfoEntityId
import org.mapstruct.InheritInverseConfiguration
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Mappings
import java.time.LocalDateTime
import java.util.*

@Mapper(
    config = ToBusinessMapperConfig::class
)
interface ProviderUserInfoMapper {

    @Mappings(
        Mapping(target = "providerId", source = "id.providerId"),
        Mapping(target = "userId", source = "id.userId"),
        Mapping(target = "userInfo", source = "entity")
    )
    fun toProviderUserInfo(entity: ProviderUserInfoEntity): ProviderUserInfo

    fun toRawProviderUserInfo(entity: ProviderUserInfoEntity): RawProviderClaims

    @InheritInverseConfiguration
    @Mappings(
        Mapping(target = "id", expression = "java(toEntityId(providerId, userId))")
    )
    fun toEntity(
        providerId: String,
        userId: UUID,
        userInfo: RawProviderClaims,
        fetchDate: LocalDateTime,
        changeDate: LocalDateTime,
        authorizeAttemptId: UUID?
    ): ProviderUserInfoEntity

    fun toEntityId(
        providerId: String,
        userId: UUID
    ): ProviderUserInfoEntityId
}

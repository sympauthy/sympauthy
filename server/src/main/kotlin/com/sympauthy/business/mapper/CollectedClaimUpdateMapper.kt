package com.sympauthy.business.mapper

import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.data.model.CollectedClaimEntity
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.Mappings
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * We do not apply the ToEntityMapperConfig for this mapper because of weird behaviour with the userId.
 */
@Mapper
abstract class CollectedClaimUpdateMapper {

    lateinit var claimValueMapper: ClaimValueMapper

    @Mappings(
        Mapping(target = "id", ignore = true),
        Mapping(target = "verified", expression = "java(null)"),
        Mapping(target = "verificationDate", expression = "java(null)"),
        Mapping(target = "collectionDate", expression = "java(java.time.LocalDateTime.now())")
    )
    abstract fun toEntity(
        userId: UUID,
        update: CollectedClaimUpdate
    ): CollectedClaimEntity

    @Mappings(
        Mapping(target = "id", ignore = true),
        Mapping(target = "userId", ignore = true),
        Mapping(target = "verified", ignore = true),
        Mapping(target = "verificationDate", ignore = true),
        Mapping(target = "collectionDate", expression = "java(java.time.LocalDateTime.now())")
    )
    abstract fun updateEntity(
        @MappingTarget entity: CollectedClaimEntity,
        update: CollectedClaimUpdate
    ): CollectedClaimEntity

    fun toClaim(claim: Claim): String = claim.id

    fun toValue(value: Optional<Any>?): String? {
        return value?.getOrNull()?.let { claimValueMapper.toEntity(it) }
    }
}

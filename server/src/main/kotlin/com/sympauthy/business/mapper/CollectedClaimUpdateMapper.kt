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
 *
 * Both spellings of the value are mapped by an expression rather than left to the generator: two methods
 * turning one `Optional<Any>` into a `String` are two candidates for the same property, and the generator
 * refuses to choose between them.
 */
@Mapper
abstract class CollectedClaimUpdateMapper {

    lateinit var claimValueMapper: ClaimValueMapper

    @Mappings(
        Mapping(target = "id", ignore = true),
        Mapping(target = "value", expression = "java(toValue(update.getValue()))"),
        Mapping(target = "comparisonValue", expression = "java(toComparisonValue(update.getValue()))"),
        Mapping(target = "verified", expression = "java(null)"),
        Mapping(target = "verificationDate", expression = "java(null)"),
        Mapping(target = "collectionDate", expression = "java(java.time.LocalDateTime.now())")
    )
    abstract fun toEntity(
        userId: UUID,
        sessionId: UUID?,
        update: CollectedClaimUpdate
    ): CollectedClaimEntity

    @Mappings(
        Mapping(target = "id", ignore = true),
        Mapping(target = "userId", ignore = true),
        Mapping(target = "sessionId", ignore = true),
        Mapping(target = "value", expression = "java(toValue(update.getValue()))"),
        Mapping(target = "comparisonValue", expression = "java(toComparisonValue(update.getValue()))"),
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

    /** The spelling [CollectedClaimEntity.comparisonValue] holds for [value]. */
    fun toComparisonValue(value: Optional<Any>?): String? {
        return claimValueMapper.toComparisonValue(value?.getOrNull())
    }
}

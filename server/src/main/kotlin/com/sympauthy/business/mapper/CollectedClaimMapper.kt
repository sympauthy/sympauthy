package com.sympauthy.business.mapper

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.data.model.CollectedClaimEntity
import org.mapstruct.Mapper

@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class CollectedClaimMapper {

    lateinit var claimManager: ClaimManager

    lateinit var claimValueMapper: ClaimValueMapper

    /**
     * Convert a collected user info entity into a business object.
     *
     * Return null if the user info cannot be converted back. For example, if the data type of custom claim
     * has changed and the data cannot be deserialized anymore.
     */
    fun toCollectedClaim(entity: CollectedClaimEntity): CollectedClaim? {
        val claim = claimManager.findByIdOrNull(entity.claim) ?: return null
        val value = if (entity.value != null) {
            claimValueMapper.toBusiness(entity.value, claim.dataType) ?: return null
        } else null
        return CollectedClaim(
            userId = entity.userId,
            claim = claim,
            value = value,
            verified = entity.verified,
            collectionDate = entity.collectionDate,
            verificationDate = entity.verificationDate
        )
    }
}

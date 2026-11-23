package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.claim.ClaimManager
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.util.wireName
import jakarta.inject.Singleton

@Singleton
class CollectedClaimMapper(
    private val claimManager: ClaimManager,
    private val claimValueMapper: ClaimValueMapper
) {

    /**
     * Convert a collected claim entity into a business object, or null where the configuration no longer
     * declares the claim the row was collected for, or turned it off.
     *
     * A value that will not read back as the type its claim declares throws an internal
     * [BusinessException] "mapper.collected_claim.invalid_property" rather than being dropped; see
     * `docs/standards/business-layer-code-standard.md`.
     */
    fun toCollectedClaim(entity: CollectedClaimEntity): CollectedClaim? {
        val claim = claimManager.findByIdOrNull(entity.claim) ?: return null
        val value = entity.value?.let {
            claimValueMapper.toBusiness(it, claim.dataType) ?: throw internalBusinessExceptionOf(
                detailsId = "mapper.collected_claim.invalid_property",
                values = arrayOf(
                    "property" to "value",
                    "claim" to entity.claim,
                    "type" to claim.dataType.wireName
                )
            )
        }
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

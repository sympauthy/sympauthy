package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.mapper.config.ToBusinessMapperConfig
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.data.model.CollectedClaimEntity
import com.sympauthy.util.wireName
import org.mapstruct.Mapper

@Mapper(
    config = ToBusinessMapperConfig::class
)
abstract class CollectedClaimMapper {

    lateinit var claimManager: ClaimManager

    lateinit var claimValueMapper: ClaimValueMapper

    /**
     * Convert a collected claim entity into a business object, or null where the configuration no longer
     * declares the claim the row was collected for.
     *
     * Those two are different answers to different questions. A claim a deployment has disabled or removed
     * is a value nothing is asking for any more, and the row is left where it is rather than failing every
     * read of the account that holds it — the operator meant that. A value that will not read back as the
     * type its claim declares is not something anybody asked for: this server wrote that row, under a type
     * the claim no longer has, and it throws an internal [BusinessException]
     * "mapper.collected_claim.invalid_property" rather than dropping it.
     *
     * Loud is the point. Dropped, the claim leaves the id token, `/userinfo`, both claim APIs and every
     * granting rule keyed on it with nothing logged anywhere, and the deployment meets it as a scope that
     * has quietly stopped being granted. What a retype costs the rows already stored is a question to ask
     * of the whole database before the restart that applies it, not of one row at whoever reads first.
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

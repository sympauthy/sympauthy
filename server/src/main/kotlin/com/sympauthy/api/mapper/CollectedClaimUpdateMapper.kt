package com.sympauthy.api.mapper

import com.sympauthy.api.exception.LocalizedAdditionalMessage
import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.ClaimValueValidator
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.exception.LocalizedException
import io.micronaut.http.HttpStatus.BAD_REQUEST
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class CollectedClaimUpdateMapper(
    @Inject private val claimManager: ClaimManager,
    @Inject private val claimValueValidator: ClaimValueValidator
) {

    fun toUpdates(values: Map<String, Any?>): List<CollectedClaimUpdate> {
        val claimUpdates = ArrayList<CollectedClaimUpdate>(values.size)
        val exceptionByClaimMap = mutableMapOf<Claim, LocalizedException>()

        for ((claimId, value) in values) {
            val claim = claimManager.findByIdOrNull(claimId) ?: continue
            try {
                val validatedAndCleanedValue = claimValueValidator.validateAndCleanValueForClaim(claim, value)
                claimUpdates.add(
                    CollectedClaimUpdate(
                        claim = claim,
                        value = validatedAndCleanedValue
                    )
                )
            } catch (ex: LocalizedException) {
                exceptionByClaimMap[claim] = ex
            }
        }

        if (exceptionByClaimMap.isNotEmpty()) {
            throw LocalizedHttpException(
                status = BAD_REQUEST,
                detailsId = "flow.claims.invalid",
                additionalInfo = exceptionByClaimMap.map { (claim, ex) ->
                    LocalizedAdditionalMessage(
                        path = claim.id,
                        descriptionId = ex.descriptionId ?: ex.detailsId
                    )
                }
            )
        }
        return claimUpdates
    }
}

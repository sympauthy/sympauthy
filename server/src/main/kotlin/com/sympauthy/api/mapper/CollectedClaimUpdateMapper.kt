package com.sympauthy.api.mapper

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

    /**
     * The updates the [values] of a payload ask for, one per claim this deployment declares, with each
     * value validated and cleaned by [ClaimValueValidator]. A key naming no claim is ignored.
     *
     * Every value is validated before any of them is refused, and a payload with one or more refused
     * throws `flow.claims.invalid` carrying [LocalizedHttpException.propertyErrors] — the failure of
     * each claim by its id, so the caller is told which values to correct rather than the first.
     *
     * That failure is an `api` one and not a [com.sympauthy.business.exception.BusinessException], so
     * `handleException` never sees it and the interactive flow session survives it: the end-user stays
     * on the step and sends the values again.
     */
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
                recoverable = true,
                detailsId = "flow.claims.invalid",
                descriptionId = "description.flow.claims.invalid",
                propertyErrors = exceptionByClaimMap.mapKeys { (claim, _) -> claim.id }
            )
        }
        return claimUpdates
    }
}

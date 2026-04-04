package com.sympauthy.business.model.flow

import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.user.CollectedClaim

/**
 * Summary of the current status of an authorization attempt going through a web authorization flow.
 */
data class WebAuthorizationFlowStatus(
    /**
     * List of claims that identify the end-user (e.g. email, phone number).
     */
    val identifierClaims: List<CollectedClaim> = emptyList(),
    /**
     * List of claims filtered by the scopes the end-user has consented to.
     */
    val consentedClaims: List<CollectedClaim> = emptyList(),
    /**
     * True if the authorization attempt failed.
     */
    val failed: Boolean = false,
    /**
     * True if no user has been authenticated for this flow yet.
     */
    val missingUser: Boolean = false,
    /**
     * True if we are missing some required claims from the end-user, and they must be collected by the client.
     */
    val missingRequiredClaims: Boolean = false,
    /**
     * List of media we must send a validation code too according to the claims collected from the end-user.
     */
    val missingMediaForClaimValidation: List<ValidationCodeMedia> = emptyList(),
    /**
     * True if MFA is required for this flow and the end-user has not yet completed the MFA step.
     */
    val missingMfa: Boolean = false,
) {

    /**
     * True if the authorization is complete and the user can be redirected to the client.
     */
    val complete: Boolean = listOf(
        missingUser,
        missingRequiredClaims,
        missingMediaForClaimValidation.isNotEmpty(),
        missingMfa,
    ).none { it }
}

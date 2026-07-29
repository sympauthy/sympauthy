package com.sympauthy.business.model.flow.auth

import com.sympauthy.business.model.code.ValidationCodeMedia

/**
 * Summary of the current progress of an ongoing OAuth2 authorization flow session.
 *
 * Computed internally by
 * [com.sympauthy.business.manager.flow.auth.OAuth2AuthorizeInteractiveFlowPurposeHandler] to decide the
 * next step; exclusive to the OAuth2 authorize purpose.
 */
data class OAuth2AuthorizeInteractiveFlowStatus(
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
)

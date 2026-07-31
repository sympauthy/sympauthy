package com.sympauthy.business.model.flow

import com.sympauthy.business.model.code.ValidationCodeMedia

/**
 * The abstract, transport-agnostic step an interactive flow session must present next.
 *
 * A step is produced by the session's [InteractiveFlowPurpose] handler purely from the session state; it never
 * carries a URI. Mapping a step to a concrete redirect URL — or to another transport later — happens at
 * the API boundary, so the business engine stays free of any transport concern.
 */
sealed interface InteractiveFlowStep {
    /**
     * The end-user must authenticate (sign in) to identify itself.
     */
    data object SignIn : InteractiveFlowStep

    /**
     * The end-user must register (sign up) a new account.
     */
    data object SignUp : InteractiveFlowStep

    /**
     * The end-user must choose which multi-factor authentication method to enroll (and may skip when the
     * enrollment is optional). The selection page auto-redirects to the method's enrollment step when there is
     * a single method to enroll and no skip is offered.
     */
    data object MfaSelectionForEnrollment : InteractiveFlowStep

    /**
     * The end-user must choose which enrolled multi-factor authentication method to challenge. The selection
     * page auto-redirects to the method's challenge step when a single method is enrolled.
     */
    data object MfaSelectionForChallenge : InteractiveFlowStep

    /**
     * The end-user must enroll a TOTP authenticator.
     */
    data object MfaTotpEnroll : InteractiveFlowStep

    /**
     * The end-user must complete a TOTP challenge.
     */
    data object MfaTotpChallenge : InteractiveFlowStep

    /**
     * The end-user must provide the claims required to continue the flow.
     */
    data object CollectClaims : InteractiveFlowStep

    /**
     * The end-user must validate a claim through the given [media], or through any available media if
     * [media] is null.
     */
    data class ValidateClaims(val media: ValidationCodeMedia?) : InteractiveFlowStep

    /**
     * The flow has failed; the end-user must be presented with the error.
     */
    data object Error : InteractiveFlowStep

    /**
     * Terminal step: the flow has completed and the end-user must be handed back to the flow's initiator.
     */
    data object Complete : InteractiveFlowStep

    /**
     * Terminal step: the end-user cancelled the flow and must be handed back to the flow's initiator on the
     * cancellation redirect (e.g. the OAuth2 `error=access_denied` response, or a caller-provided cancel URL).
     */
    data object Cancel : InteractiveFlowStep
}

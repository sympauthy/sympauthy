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
     * The end-user must go through multi-factor authentication (method selection / routing).
     */
    data object Mfa : InteractiveFlowStep

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
}

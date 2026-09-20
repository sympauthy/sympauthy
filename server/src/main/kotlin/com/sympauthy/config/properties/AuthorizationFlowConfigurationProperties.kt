package com.sympauthy.config.properties

import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties.Companion.AUTHORIZATION_FLOWS_KEY
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

@EachProperty(AUTHORIZATION_FLOWS_KEY)
class AuthorizationFlowConfigurationProperties(
    @param:Parameter val id: String
) {
    var type: String? = null

    var root: String? = null
    var signIn: String? = null
    var collectClaims: String? = null
    var validateClaims: String? = null
    var error: String? = null

    /** Where sign-up is served. Required only where sign-up or invitation is enabled. */
    var signUp: String? = null

    /** Where the confirm page is served. Required only where a confirm-gated flow is configured. */
    var confirm: String? = null

    /** Where a second factor is picked to enrol. Required only where MFA is enabled. */
    var mfaSelectionForEnrollment: String? = null

    /** Where a second factor is picked to answer a challenge. Required only where MFA is enabled. */
    var mfaSelectionForChallenge: String? = null

    /** Where a TOTP enrolment is completed. Required only where MFA is enabled. */
    var mfaTotpEnroll: String? = null

    /** Where a TOTP challenge is answered. Required only where MFA is enabled. */
    var mfaTotpChallenge: String? = null

    companion object {
        const val AUTHORIZATION_FLOWS_KEY = "flows"
    }
}

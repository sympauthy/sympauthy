package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

sealed class MfaRoutingResult

data class MfaAutoRedirect(val uri: URI) : MfaRoutingResult()

data class MfaMethodSelection(
    val methods: List<AvailableMfaMethod>,
    val skipUri: URI
) : MfaRoutingResult()

data class AvailableMfaMethod(val name: String, val uri: URI)

@Singleton
class WebAuthorizationFlowMfaManager(
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val totpManager: TotpManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder
) {

    /**
     * Returns the [MfaRoutingResult] describing what the end-user must do for the MFA step.
     *
     * Routing table:
     * | required | enrolled | Result                                                  |
     * |----------|----------|---------------------------------------------------------|
     * | true     | false    | Auto-redirect to TOTP enrollment                        |
     * | true     | true     | Auto-redirect to TOTP challenge                         |
     * | false    | false    | Method selection screen: TOTP enrollment offer + skip   |
     * | false    | true     | Method selection screen: TOTP challenge + skip          |
     */
    suspend fun getMfaResult(
        authorizeAttempt: AuthorizeAttempt,
        user: User,
        flow: WebAuthorizationFlow,
        skipEndpointPath: String
    ): MfaRoutingResult {
        val mfaConfig = uncheckedMfaConfig.orThrow()
        val hasEnrollment = totpManager.findConfirmedEnrollments(user.id).isNotEmpty()

        return when {
            mfaConfig.required && !hasEnrollment ->
                MfaAutoRedirect(redirectUriBuilder.getMfaTotpEnrollUri(authorizeAttempt, flow))

            mfaConfig.required ->
                MfaAutoRedirect(redirectUriBuilder.getMfaTotpChallengeUri(authorizeAttempt, flow))

            else -> {
                val methodUri = if (hasEnrollment) {
                    redirectUriBuilder.getMfaTotpChallengeUri(authorizeAttempt, flow)
                } else {
                    redirectUriBuilder.getMfaTotpEnrollUri(authorizeAttempt, flow)
                }
                MfaMethodSelection(
                    methods = listOf(AvailableMfaMethod(name = "TOTP", uri = methodUri)),
                    skipUri = redirectUriBuilder.getMfaSkipUri(authorizeAttempt, skipEndpointPath)
                )
            }
        }
    }

    /**
     * Marks the MFA step as passed without the end-user completing a challenge.
     *
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if MFA is required
     * (`mfa.required=true`), since skipping is not permitted in that case.
     */
    suspend fun skipMfa(authorizeAttempt: OnGoingAuthorizeAttempt): OnGoingAuthorizeAttempt {
        if (uncheckedMfaConfig.orThrow().required) {
            throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed")
        }
        return authorizeAttemptManager.setMfaPassed(authorizeAttempt)
    }
}

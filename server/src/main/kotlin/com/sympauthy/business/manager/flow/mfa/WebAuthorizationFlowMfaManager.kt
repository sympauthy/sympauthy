package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
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
    @Inject private val totpManager: TotpManager,
    @Inject private val redirectUriBuilder: WebAuthorizationFlowRedirectUriBuilder
) {

    /**
     * Returns the [MfaRoutingResult] describing what the end-user must do for the MFA step.
     *
     * Routing table:
     * | required | enrolled | Result                                     |
     * |----------|----------|--------------------------------------------|
     * | true     | false    | Auto-redirect to TOTP enrollment           |
     * | true     | true     | Auto-redirect to TOTP challenge            |
     * | false    | true     | Method selection screen with a skip option |
     */
    suspend fun getMfaResult(
        authorizeAttempt: AuthorizeAttempt,
        user: User,
        flow: WebAuthorizationFlow,
        skipEndpointPath: String
    ): MfaRoutingResult {
        val mfaConfig = uncheckedMfaConfig as? EnabledMfaConfig
        val hasEnrollment = totpManager.findConfirmedEnrollments(user.id).isNotEmpty()

        return when {
            mfaConfig?.required == true && !hasEnrollment ->
                MfaAutoRedirect(redirectUriBuilder.getMfaTotpEnrollUri(authorizeAttempt, flow))

            mfaConfig?.required == true ->
                MfaAutoRedirect(redirectUriBuilder.getMfaTotpChallengeUri(authorizeAttempt, flow))

            hasEnrollment -> MfaMethodSelection(
                methods = listOf(
                    AvailableMfaMethod(
                        name = "TOTP",
                        uri = redirectUriBuilder.getMfaTotpChallengeUri(authorizeAttempt, flow)
                    )
                ),
                skipUri = redirectUriBuilder.getMfaSkipUri(authorizeAttempt, skipEndpointPath)
            )

            // mfa.required=false and no enrollment: missingMfa is false so this branch
            // should never be reached in normal flow.
            else -> MfaAutoRedirect(redirectUriBuilder.getMfaTotpEnrollUri(authorizeAttempt, flow))
        }
    }
}

package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.flow.WebAuthorizationFlowRedirectUriBuilder
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.User
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

sealed class MfaRoutingResult

data class MfaAutoRedirect(val uri: URI) : MfaRoutingResult()

data class MfaMethodSelection(
    val methods: List<AvailableMfaMethod>,
    val skipUri: URI?
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
     * | required | enrolled methods | Result                                                  |
     * |----------|------------------|---------------------------------------------------------|
     * | true     | 0                | Auto-redirect to TOTP enrollment                        |
     * | any      | 1                | Auto-redirect to enrolled method challenge               |
     * | any      | 2+ (future)      | Method selection: enrolled methods only, no skip         |
     * | false    | 0                | Method selection: all methods as enrollment offers + skip |
     */
    suspend fun getMfaResult(
        authorizeAttempt: AuthorizeAttempt,
        user: User,
        flow: WebAuthorizationFlow,
        skipEndpointPath: String
    ): MfaRoutingResult {
        val mfaConfig = uncheckedMfaConfig.orThrow()
        val enrollments = totpManager.findConfirmedEnrollments(user.id)

        return when {
            // User is enrolled in exactly one method — go straight to its challenge.
            enrollments.size == 1 ->
                MfaAutoRedirect(redirectUriBuilder.getMfaTotpChallengeUri(authorizeAttempt, flow))

            // User is enrolled in multiple methods (future) — let them pick, but no skip.
            enrollments.size > 1 ->
                MfaMethodSelection(
                    methods = listOf(
                        AvailableMfaMethod(
                            name = "TOTP",
                            uri = redirectUriBuilder.getMfaTotpChallengeUri(authorizeAttempt, flow)
                        )
                    ),
                    skipUri = null
                )

            // Not enrolled + required — force enrollment in the only available method.
            mfaConfig.required ->
                MfaAutoRedirect(redirectUriBuilder.getMfaTotpEnrollUri(authorizeAttempt, flow))

            // Not enrolled + optional — offer enrollment with the option to skip.
            else ->
                MfaMethodSelection(
                    methods = listOf(
                        AvailableMfaMethod(
                            name = "TOTP",
                            uri = redirectUriBuilder.getMfaTotpEnrollUri(authorizeAttempt, flow)
                        )
                    ),
                    skipUri = redirectUriBuilder.getMfaSkipUri(authorizeAttempt, skipEndpointPath)
                )
        }
    }

    /**
     * Marks the MFA step as passed without the end-user completing a challenge.
     *
     * Throws an unrecoverable [com.sympauthy.business.exception.BusinessException] if:
     * - MFA is required (`mfa.required=true`), or
     * - the end-user has already enrolled in at least one MFA method.
     */
    suspend fun skipMfa(authorizeAttempt: OnGoingAuthorizeAttempt): OnGoingAuthorizeAttempt {
        if (uncheckedMfaConfig.orThrow().required) {
            throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed")
        }
        val userId = authorizeAttempt.userId
            ?: throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed")
        if (totpManager.findConfirmedEnrollments(userId).isNotEmpty()) {
            throw internalBusinessExceptionOf("flow.mfa.skip.not_allowed_when_enrolled")
        }
        return authorizeAttemptManager.setMfaPassed(authorizeAttempt)
    }
}

package com.sympauthy.config.validation

import com.sympauthy.business.flow.model.AuthorizationFlow
import com.sympauthy.business.flow.model.AuthorizationFlow.Companion.DEFAULT_WEB_AUTHORIZATION_FLOW_ENDPOINT
import com.sympauthy.business.flow.model.AuthorizationFlow.Companion.DEFAULT_WEB_AUTHORIZATION_FLOW_ID
import com.sympauthy.business.flow.model.AuthorizationFlowType
import com.sympauthy.business.flow.model.InteractiveFlow
import com.sympauthy.business.flow.model.ResetPasswordPages
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedAuthorizationFlow
import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties.Companion.AUTHORIZATION_FLOWS_KEY
import com.sympauthy.platform.util.mergeUri
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class AuthorizationFlowsConfigValidator {

    /**
     * Build the flows the deployment configured. [rootUri] is the deployment's root URL, which a flow
     * declaring no root of its own falls back to. A flow must offer the MFA pages when [totpMfaEnabled].
     */
    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedAuthorizationFlow>,
        rootUri: URI,
        totpMfaEnabled: Boolean
    ): List<AuthorizationFlow> {
        return parsed.mapNotNull { flow ->
            when (flow.type) {
                AuthorizationFlowType.WEB -> validateWebFlow(ctx, flow, rootUri, totpMfaEnabled)
                null -> null // Parse error, already reported.
            }
        }
    }

    /**
     * Build the interactive flow bundled with this authorization server, whose pages it serves itself
     * under [rootUri].
     */
    fun bundledFlow(rootUri: URI): InteractiveFlow {
        val flowUri = UriBuilder.of(rootUri).path(DEFAULT_WEB_AUTHORIZATION_FLOW_ENDPOINT).build()
        return InteractiveFlow(
            id = DEFAULT_WEB_AUTHORIZATION_FLOW_ID,
            signInUri = UriBuilder.of(flowUri).path("sign-in").build(),
            signUpUri = UriBuilder.of(flowUri).path("sign-up").build(),
            mfaSelectionForEnrollmentUri = UriBuilder.of(flowUri).path("mfa/enrollment").build(),
            mfaSelectionForChallengeUri = UriBuilder.of(flowUri).path("mfa/challenge").build(),
            mfaTotpChallengeUri = UriBuilder.of(flowUri).path("mfa/totp").build(),
            mfaTotpEnrollUri = UriBuilder.of(flowUri).path("mfa/totp/enroll").build(),
            collectClaimsUri = UriBuilder.of(flowUri).path("claims/edit").build(),
            validateClaimsUri = UriBuilder.of(flowUri).path("claims/validate").build(),
            errorUri = UriBuilder.of(flowUri).path("error").build(),
            confirmUri = UriBuilder.of(flowUri).path("confirm").build(),
        )
    }

    private fun validateWebFlow(
        ctx: ConfigParsingContext,
        parsed: ParsedAuthorizationFlow,
        rootUri: URI,
        totpMfaEnabled: Boolean
    ): InteractiveFlow? {
        val subCtx = ctx.child()
        val configKeyPrefix = "$AUTHORIZATION_FLOWS_KEY.${parsed.id}"

        // Resolve root URI: flow-specific root or fall back to the deployment's own.
        val flowRootUri = parsed.rootUri ?: rootUri

        val signInUri = resolveUri(flowRootUri, parsed.signInUri)
        val signUpUri = parsed.signUpUri?.let { resolveUri(flowRootUri, it) }
        val confirmUri = parsed.confirmUri?.let { resolveUri(flowRootUri, it) }
        val collectClaimsUri = resolveUri(flowRootUri, parsed.collectClaimsUri)
        val validateClaimsUri = resolveUri(flowRootUri, parsed.validateClaimsUri)
        val errorUri = resolveUri(flowRootUri, parsed.errorUri)
        val mfaSelectionForEnrollmentUri = parsed.mfaSelectionForEnrollmentUri?.let { resolveUri(flowRootUri, it) }
        val mfaSelectionForChallengeUri = parsed.mfaSelectionForChallengeUri?.let { resolveUri(flowRootUri, it) }
        val mfaTotpEnrollUri = parsed.mfaTotpEnrollUri?.let { resolveUri(flowRootUri, it) }
        val mfaTotpChallengeUri = parsed.mfaTotpChallengeUri?.let { resolveUri(flowRootUri, it) }

        // MFA cross-reference validation: both selection pages are needed whenever MFA is enabled, since a
        // sign-up can lead to enrollment (even when optional) and a sign-in of an enrolled user to a challenge.
        if (totpMfaEnabled && mfaSelectionForEnrollmentUri == null) {
            subCtx.addError(
                configExceptionOf(
                    "$configKeyPrefix.mfa-selection-for-enrollment",
                    "config.flow.mfa.selection_for_enrollment.missing"
                )
            )
        }
        if (totpMfaEnabled && mfaSelectionForChallengeUri == null) {
            subCtx.addError(
                configExceptionOf(
                    "$configKeyPrefix.mfa-selection-for-challenge",
                    "config.flow.mfa.selection_for_challenge.missing"
                )
            )
        }
        if (totpMfaEnabled && mfaTotpEnrollUri == null) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.mfa-totp-enroll", "config.flow.mfa.totp.enroll.missing")
            )
        }
        if (totpMfaEnabled && mfaTotpChallengeUri == null) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.mfa-totp-challenge", "config.flow.mfa.totp.challenge.missing")
            )
        }

        val resetPassword = validateResetPasswordPages(subCtx, parsed, flowRootUri)

        ctx.merge(subCtx)
        if (subCtx.hasErrors || signInUri == null || collectClaimsUri == null ||
            validateClaimsUri == null || errorUri == null
        ) {
            return null
        }

        return InteractiveFlow(
            id = parsed.id,
            signInUri = signInUri,
            signUpUri = signUpUri,
            confirmUri = confirmUri,
            collectClaimsUri = collectClaimsUri,
            validateClaimsUri = validateClaimsUri,
            errorUri = errorUri,
            mfaSelectionForEnrollmentUri = mfaSelectionForEnrollmentUri,
            mfaSelectionForChallengeUri = mfaSelectionForChallengeUri,
            mfaTotpEnrollUri = mfaTotpEnrollUri,
            mfaTotpChallengeUri = mfaTotpChallengeUri,
            resetPassword = resetPassword
        )
    }

    /**
     * Build the reset pages [parsed] names, or null where it names none of them. A flow naming some of them and
     * not all is refused, naming the keys it is missing: its sign-in page would offer a reset leading to a page
     * the flow does not have.
     */
    private fun validateResetPasswordPages(
        ctx: ConfigParsingContext,
        parsed: ParsedAuthorizationFlow,
        flowRootUri: URI
    ): ResetPasswordPages? {
        val configKeyPrefix = "$AUTHORIZATION_FLOWS_KEY.${parsed.id}"
        val pages = mapOf(
            "$configKeyPrefix.reset-password" to resolveUri(flowRootUri, parsed.resetPasswordUri),
            "$configKeyPrefix.reset-password-validate" to resolveUri(flowRootUri, parsed.resetPasswordValidateUri),
            "$configKeyPrefix.reset-password-choose" to resolveUri(flowRootUri, parsed.resetPasswordChooseUri)
        )
        val (namedKeys, missingKeys) = pages.keys.partition { pages[it] != null }
        if (namedKeys.isEmpty()) return null
        if (missingKeys.isNotEmpty()) {
            missingKeys.forEach { key ->
                ctx.addError(
                    configExceptionOf(
                        key, "config.flow.reset_password.missing",
                        "namedKeys" to namedKeys.joinToString()
                    )
                )
            }
            return null
        }
        val (resetPasswordUri, validateUri, chooseUri) = pages.values.map { it!! }
        return ResetPasswordPages(
            resetPasswordUri = resetPasswordUri,
            validateUri = validateUri,
            chooseUri = chooseUri
        )
    }

    private fun resolveUri(rootUri: URI?, uri: URI?): URI? {
        if (uri == null) return null
        return rootUri?.let { mergeUri(it, uri) } ?: uri
    }
}

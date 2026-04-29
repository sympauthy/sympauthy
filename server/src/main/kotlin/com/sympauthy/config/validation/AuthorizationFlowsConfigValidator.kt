package com.sympauthy.config.validation

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.AuthorizationFlowType
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.parsing.ParsedAuthorizationFlow
import com.sympauthy.config.properties.AuthorizationFlowConfigurationProperties.Companion.AUTHORIZATION_FLOWS_KEY
import com.sympauthy.util.mergeUri
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class AuthorizationFlowsConfigValidator(
    @Inject private val uncheckedUrlsConfig: UrlsConfig,
    @Inject private val uncheckedMfaConfig: MfaConfig
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedAuthorizationFlow>
    ): List<AuthorizationFlow> {
        return parsed.mapNotNull { flow ->
            when (flow.type) {
                AuthorizationFlowType.WEB -> validateWebFlow(ctx, flow)
                null -> null // Parse error, already reported.
            }
        }
    }

    private fun validateWebFlow(
        ctx: ConfigParsingContext,
        parsed: ParsedAuthorizationFlow
    ): WebAuthorizationFlow? {
        val subCtx = ctx.child()
        val configKeyPrefix = "$AUTHORIZATION_FLOWS_KEY.${parsed.id}"

        // Resolve root URI: flow-specific root or fall back to UrlsConfig.
        val rootUri = parsed.rootUri
            ?: (uncheckedUrlsConfig as? EnabledUrlsConfig)?.root

        val signInUri = resolveUri(rootUri, parsed.signInUri)
        val signUpUri = parsed.signUpUri?.let { resolveUri(rootUri, it) }
        val collectClaimsUri = resolveUri(rootUri, parsed.collectClaimsUri)
        val validateClaimsUri = resolveUri(rootUri, parsed.validateClaimsUri)
        val errorUri = resolveUri(rootUri, parsed.errorUri)
        val mfaUri = parsed.mfaUri?.let { resolveUri(rootUri, it) }
        val mfaTotpEnrollUri = parsed.mfaTotpEnrollUri?.let { resolveUri(rootUri, it) }
        val mfaTotpChallengeUri = parsed.mfaTotpChallengeUri?.let { resolveUri(rootUri, it) }

        // MFA cross-reference validation.
        val mfaConfig = uncheckedMfaConfig as? EnabledMfaConfig
        if (mfaConfig?.required == true && mfaUri == null) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.mfa", "config.flow.mfa.missing")
            )
        }
        if (mfaConfig?.totp == true && mfaTotpEnrollUri == null) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.mfa-totp-enroll", "config.flow.mfa.totp.enroll.missing")
            )
        }
        if (mfaConfig?.totp == true && mfaTotpChallengeUri == null) {
            subCtx.addError(
                configExceptionOf("$configKeyPrefix.mfa-totp-challenge", "config.flow.mfa.totp.challenge.missing")
            )
        }

        ctx.merge(subCtx)
        if (subCtx.hasErrors || signInUri == null || collectClaimsUri == null ||
            validateClaimsUri == null || errorUri == null
        ) {
            return null
        }

        return WebAuthorizationFlow(
            id = parsed.id,
            signInUri = signInUri,
            signUpUri = signUpUri,
            collectClaimsUri = collectClaimsUri,
            validateClaimsUri = validateClaimsUri,
            errorUri = errorUri,
            mfaUri = mfaUri,
            mfaTotpEnrollUri = mfaTotpEnrollUri,
            mfaTotpChallengeUri = mfaTotpChallengeUri
        )
    }

    private fun resolveUri(rootUri: URI?, uri: URI?): URI? {
        if (uri == null) return null
        return rootUri?.let { mergeUri(it, uri) } ?: uri
    }
}
